package com.shortbridge.publish.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortbridge.common.storage.StorageClient;
import com.shortbridge.connect.connector.ConnectorProperties;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.repository.SocialAccountRepository;
import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.publish.dto.PublishOutcome;
import com.shortbridge.publish.publisher.PublishContext;
import com.shortbridge.publish.publisher.PublisherHttp;
import com.shortbridge.publish.publisher.SocialPublisher;
import com.shortbridge.support.security.token.PlatformTokenCipher;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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
  private final PublisherHttp http;

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

    String snippetJson = buildSnippetJson(context, video);
    String boundary = "shortbridge-boundary-" + System.nanoTime();
    byte[] prefix = multipartPrefix(boundary, snippetJson, video);
    byte[] suffix = multipartSuffix(boundary);

    try {
      HttpResponse<String> response = uploadVideo(accessToken, boundary, prefix, suffix, video);
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
          response = uploadVideo(newToken, boundary, prefix, suffix, video);
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

      JsonNode root = http.parse(responseBody);
      String videoId = root.path("id").asText(null);
      if (videoId == null || videoId.isBlank()) {
        return PublishOutcome.failedPermanent("YT_NO_ID", responseBody);
      }
      log.info("youtube publish success: videoId={} url=https://youtube.com/watch?v={}", videoId, videoId);
      return PublishOutcome.success(videoId, null, responseBody);
    } catch (UncheckedIOException e) {
      log.error("youtube video read failed: storageKey={}", video.getStorageKey(), e);
      return PublishOutcome.failedTemporary(
          "STORAGE_READ_FAILED", e.getMessage(), Instant.now().plusSeconds(60));
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      log.error("youtube videos.insert IO error", e);
      return PublishOutcome.failedTemporary(
          "YT_IO_ERROR", e.getMessage(), Instant.now().plusSeconds(60));
    }
  }

  private HttpResponse<String> uploadVideo(
      String accessToken, String boundary, byte[] prefix, byte[] suffix, Video video)
      throws IOException, InterruptedException {
    long total = (long) prefix.length + video.getFileSize() + suffix.length;
    Supplier<InputStream> bodyStream =
        () ->
            new SequenceInputStream(
                Collections.enumeration(
                    List.of(
                        new ByteArrayInputStream(prefix),
                        storageClient.open(video.getStorageKey()),
                        new ByteArrayInputStream(suffix))));
    HttpRequest.BodyPublisher body =
        HttpRequest.BodyPublishers.fromPublisher(
            HttpRequest.BodyPublishers.ofInputStream(bodyStream), total);
    HttpRequest request =
        http.bearer(UPLOAD_URL, accessToken)
            .timeout(Duration.ofMinutes(30))
            .header("Content-Type", "multipart/related; boundary=" + boundary)
            .header("Accept", "application/json")
            .POST(body)
            .build();
    return http.send(request);
  }

  private String buildSnippetJson(PublishContext context, Video video) {
    Map<String, Object> snippet = new HashMap<>();
    String title = context.target().getPlatformTitle();
    if (title == null || title.isBlank()) title = video.getOriginalFileName();
    snippet.put("title", PublisherHttp.limit(title, 100));
    String description = context.target().getPlatformDescription();
    if (description != null && !description.isBlank()) {
      snippet.put("description", PublisherHttp.limit(description, 5000));
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
      return http.writeJson(root);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] multipartPrefix(String boundary, String snippetJson, Video video) {
    String mime = video.getMimeType() == null ? "video/mp4" : video.getMimeType();
    String s =
        "--" + boundary + "\r\n"
            + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
            + snippetJson + "\r\n"
            + "--" + boundary + "\r\n"
            + "Content-Type: " + mime + "\r\n\r\n";
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private byte[] multipartSuffix(String boundary) {
    return ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
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
      HttpResponse<String> resp = http.send(req);
      if (resp.statusCode() / 100 != 2) {
        log.warn("youtube refresh failed: status={} body={}", resp.statusCode(), resp.body());
        return null;
      }
      JsonNode json = http.parse(resp.body());
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
