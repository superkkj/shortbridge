package com.shortbridge.publish.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbridge.common.storage.StorageClient;
import com.shortbridge.connect.connector.ConnectorProperties;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.repository.SocialAccountRepository;
import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.publish.dto.PublishOutcome;
import com.shortbridge.publish.publisher.PublishContext;
import com.shortbridge.publish.publisher.SocialPublisher;
import com.shortbridge.support.security.token.PlatformTokenCipher;
import java.net.URLEncoder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class YouTubePublisher implements SocialPublisher {

  private static final String UPLOAD_URL =
      "https://www.googleapis.com/upload/youtube/v3/videos?part=snippet%2Cstatus&uploadType=multipart";
  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

  private final PlatformTokenCipher tokenCipher;
  private final StorageClient storageClient;
  private final ConnectorProperties connectorProperties;
  private final SocialAccountRepository socialAccountRepository;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

  @Override
  public Platform platform() {
    return Platform.YOUTUBE;
  }

  @Override
  public PublishOutcome publish(PublishContext context) {
    SocialAccount account = context.socialAccount();
    Video video = context.video();
    String accessToken;
    try {
      accessToken = tokenCipher.decrypt(account.getAccessTokenEncrypted());
    } catch (RuntimeException e) {
      log.warn("youtube token decrypt failed: account={}", account.getId(), e);
      return PublishOutcome.reconnectRequired("token decrypt failed: " + e.getMessage());
    }
    if (accessToken == null || accessToken.isBlank()) {
      return PublishOutcome.reconnectRequired("access token empty");
    }

    byte[] videoBytes;
    try (InputStream is = storageClient.open(video.getStorageKey())) {
      videoBytes = is.readAllBytes();
    } catch (IOException e) {
      log.error("youtube video read failed: storageKey={}", video.getStorageKey(), e);
      return PublishOutcome.failedTemporary(
          "STORAGE_READ_FAILED", e.getMessage(), Instant.now().plusSeconds(60));
    }

    String snippetJson = buildSnippetJson(context, video);
    String boundary = "shortbridge-boundary-" + System.nanoTime();
    byte[] body = buildMultipartBody(boundary, snippetJson, video, videoBytes);

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(UPLOAD_URL))
            .timeout(Duration.ofMinutes(5))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "multipart/related; boundary=" + boundary)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      String responseBody = response.body();
      log.info(
          "youtube videos.insert response: status={} bodyHead={}",
          status,
          responseBody == null ? "" : responseBody.substring(0, Math.min(300, responseBody.length())));

      if (status == 401) {
        log.info("youtube 401 detected, attempting token refresh");
        String newToken = refreshAccessToken(account);
        if (newToken != null) {
          HttpRequest retry =
              HttpRequest.newBuilder(URI.create(UPLOAD_URL))
                  .timeout(Duration.ofMinutes(5))
                  .header("Authorization", "Bearer " + newToken)
                  .header("Content-Type", "multipart/related; boundary=" + boundary)
                  .header("Accept", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                  .build();
          response = httpClient.send(retry, HttpResponse.BodyHandlers.ofString());
          status = response.statusCode();
          responseBody = response.body();
          log.info("youtube retry after refresh: status={}", status);
        }
        if (status == 401) {
          return PublishOutcome.reconnectRequired("youtube auth failed after refresh: status=401");
        }
      }
      if (status == 403) {
        return PublishOutcome.reconnectRequired("youtube auth failed: status=" + status);
      }
      if (status == 429) {
        return PublishOutcome.blockedByQuota(Instant.now().plusSeconds(3600));
      }
      if (status / 100 != 2) {
        if (status / 100 == 5) {
          return PublishOutcome.failedTemporary(
              "YT_" + status, responseBody, Instant.now().plusSeconds(120));
        }
        return PublishOutcome.failedPermanent("YT_" + status, responseBody);
      }

      JsonNode root = mapper.readTree(responseBody);
      String videoId = root.path("id").asText(null);
      if (videoId == null || videoId.isBlank()) {
        return PublishOutcome.failedPermanent("YT_NO_ID", responseBody);
      }
      log.info("youtube publish success: videoId={} url=https://youtube.com/watch?v={}", videoId, videoId);
      return PublishOutcome.success(videoId, null, responseBody);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("youtube videos.insert IO error", e);
      return PublishOutcome.failedTemporary(
          "YT_IO_ERROR", e.getMessage(), Instant.now().plusSeconds(60));
    }
  }

  private String buildSnippetJson(PublishContext context, Video video) {
    Map<String, Object> snippet = new HashMap<>();
    String title = context.target().getPlatformTitle();
    if (title == null || title.isBlank()) title = video.getOriginalFileName();
    snippet.put("title", limit(title, 100));
    String description = context.target().getPlatformDescription();
    if (description != null && !description.isBlank()) {
      snippet.put("description", limit(description, 5000));
    }
    if (context.hashtags() != null && !context.hashtags().isEmpty()) {
      snippet.put("tags", context.hashtags().stream().limit(15).toList());
    }
    snippet.put("categoryId", "22");

    Map<String, Object> status = new HashMap<>();
    status.put("privacyStatus", "public");
    status.put("selfDeclaredMadeForKids", false);

    Map<String, Object> root = new HashMap<>();
    root.put("snippet", snippet);
    root.put("status", status);
    try {
      return mapper.writeValueAsString(root);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] buildMultipartBody(String boundary, String snippetJson, Video video, byte[] videoBytes) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      String prefix =
          "--" + boundary + "\r\n"
              + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
              + snippetJson + "\r\n"
              + "--" + boundary + "\r\n"
              + "Content-Type: " + (video.getMimeType() == null ? "video/mp4" : video.getMimeType()) + "\r\n\r\n";
      String suffix = "\r\n--" + boundary + "--\r\n";
      out.write(prefix.getBytes(StandardCharsets.UTF_8));
      out.write(videoBytes);
      out.write(suffix.getBytes(StandardCharsets.UTF_8));
      return out.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String limit(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }

  @SuppressWarnings("unused")
  private static List<String> unused() {
    return List.of();
  }

  private String refreshAccessToken(SocialAccount account) {
    try {
      String refreshToken = tokenCipher.decrypt(account.getRefreshTokenEncrypted());
      if (refreshToken == null || refreshToken.isBlank()) {
        log.warn("youtube refresh: refresh_token empty");
        return null;
      }
      ConnectorProperties.Provider yt = connectorProperties.youtube();
      String body =
          "client_id=" + URLEncoder.encode(yt.clientId(), StandardCharsets.UTF_8)
              + "&client_secret=" + URLEncoder.encode(yt.clientSecret(), StandardCharsets.UTF_8)
              + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
              + "&grant_type=refresh_token";
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(TOKEN_URL))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        log.warn("youtube refresh failed: status={} body={}", resp.statusCode(), resp.body());
        return null;
      }
      JsonNode json = mapper.readTree(resp.body());
      String newAccess = json.path("access_token").asText(null);
      long expiresIn = json.path("expires_in").asLong(3600);
      if (newAccess == null) return null;
      account.updateToken(
          tokenCipher.encrypt(newAccess),
          account.getRefreshTokenEncrypted(),
          tokenCipher.activeVersion(),
          Instant.now().plusSeconds(expiresIn));
      socialAccountRepository.save(account);
      log.info("youtube token refreshed: account={} new expires_in={}s", account.getId(), expiresIn);
      return newAccess;
    } catch (Exception e) {
      log.warn("youtube refresh exception", e);
      return null;
    }
  }
}
