package com.shortbridge.publish.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortbridge.common.logging.SensitiveLog;
import com.shortbridge.common.storage.StorageClient;
import com.shortbridge.connect.connector.ConnectorProperties;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.publish.dto.PublishOutcome;
import com.shortbridge.publish.publisher.PublishContext;
import com.shortbridge.publish.publisher.PublisherHttp;
import com.shortbridge.publish.publisher.SocialPublisher;
import com.shortbridge.support.security.token.PlatformTokenCipher;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstagramPublisher implements SocialPublisher {

  private static final String DEFAULT_GRAPH_BASE = "https://graph.facebook.com/v23.0";

  private final PlatformTokenCipher tokenCipher;
  private final StorageClient storageClient;
  private final ConnectorProperties connectorProperties;
  private final PublisherHttp http;

  @Override
  public Platform platform() {
    return Platform.INSTAGRAM;
  }

  @Override
  public PublishOutcome publish(PublishContext context) {
    SocialAccount account = context.socialAccount();
    Video video = context.video();
    String accessToken;
    try {
      accessToken = tokenCipher.decrypt(account.getAccessTokenEncrypted());
    } catch (RuntimeException e) {
      log.warn("instagram token decrypt failed: account={}", account.getId(), e);
      return PublishOutcome.reconnectRequired("token decrypt failed: " + e.getMessage());
    }
    if (accessToken == null || accessToken.isBlank()) {
      return PublishOutcome.reconnectRequired("access token empty");
    }
    if (account.getPlatformUserId() == null || account.getPlatformUserId().isBlank()) {
      return PublishOutcome.reconnectRequired("instagram user id empty");
    }

    String videoUrl = storageClient.publicUrl(video.getStorageKey());
    if (!isPublicHttpUrl(videoUrl)) {
      return PublishOutcome.blockedByCapability("Instagram requires a public video URL: " + videoUrl);
    }

    try {
      JsonNode container = createReelsContainer(context, account.getPlatformUserId(), accessToken, videoUrl);
      if (container == null) {
        return PublishOutcome.failedTemporary("IG_CONTAINER_CREATE_FAILED", "container creation failed", Instant.now().plusSeconds(120));
      }
      String containerId = container.path("id").asText(null);
      if (containerId == null || containerId.isBlank()) {
        return PublishOutcome.failedPermanent("IG_CONTAINER_NO_ID", container.toString());
      }

      PublishOutcome statusOutcome = waitUntilReady(containerId, accessToken);
      if (statusOutcome != null) return statusOutcome;

      return publishContainer(account.getPlatformUserId(), containerId, accessToken);
    } catch (InstagramAuthException e) {
      return PublishOutcome.reconnectRequired(e.getMessage());
    } catch (InstagramQuotaException e) {
      return PublishOutcome.blockedByQuota(Instant.now().plusSeconds(3600));
    }
  }

  private JsonNode createReelsContainer(
      PublishContext context, String instagramUserId, String accessToken, String videoUrl) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("media_type", "REELS");
    form.put("video_url", videoUrl);
    String caption = caption(context);
    if (caption != null && !caption.isBlank()) {
      form.put("caption", caption);
    }
    return postForm(graphBase() + "/" + encPath(instagramUserId) + "/media", accessToken, form, "instagram media container");
  }

  private PublishOutcome waitUntilReady(String containerId, String accessToken) {
    for (int i = 0; i < 12; i++) {
      JsonNode status = getJson(
          graphBase() + "/" + encPath(containerId) + "?fields=status_code",
          accessToken,
          "instagram container status");
      if (status == null) {
        return PublishOutcome.failedTemporary("IG_STATUS_FAILED", "container status failed", Instant.now().plusSeconds(60));
      }
      String code = status.path("status_code").asText("");
      if ("FINISHED".equals(code)) {
        return null;
      }
      if ("PUBLISHED".equals(code)) {
        return PublishOutcome.success(containerId, containerId, status.toString());
      }
      if ("ERROR".equals(code) || "EXPIRED".equals(code)) {
        return PublishOutcome.failedPermanent("IG_CONTAINER_" + code, status.toString());
      }
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return PublishOutcome.failedTemporary("IG_INTERRUPTED", e.getMessage(), Instant.now().plusSeconds(60));
      }
    }
    return PublishOutcome.failedTemporary("IG_CONTAINER_IN_PROGRESS", "container is still processing", Instant.now().plusSeconds(60));
  }

  private PublishOutcome publishContainer(String instagramUserId, String containerId, String accessToken) {
    JsonNode root =
        postForm(
            graphBase() + "/" + encPath(instagramUserId) + "/media_publish",
            accessToken,
            Map.of("creation_id", containerId),
            "instagram media_publish");
    if (root == null) {
      return PublishOutcome.failedTemporary("IG_PUBLISH_FAILED", "media_publish failed", Instant.now().plusSeconds(120));
    }
    String mediaId = root.path("id").asText(null);
    if (mediaId == null || mediaId.isBlank()) {
      return PublishOutcome.failedPermanent("IG_PUBLISH_NO_ID", root.toString());
    }
    log.info("instagram publish success: mediaId={}", mediaId);
    return PublishOutcome.success(mediaId, containerId, root.toString());
  }

  private JsonNode postForm(String url, String accessToken, Map<String, String> form, String opName) {
    HttpRequest request =
        http.bearer(url, accessToken)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(form(form)))
            .build();
    return executeForJson(request, opName);
  }

  private JsonNode getJson(String url, String accessToken, String opName) {
    HttpRequest request =
        http.bearer(url, accessToken)
            .header("Accept", "application/json")
            .GET()
            .build();
    return executeForJson(request, opName);
  }

  private JsonNode executeForJson(HttpRequest request, String opName) {
    try {
      HttpResponse<String> response = http.send(request);
      int status = response.statusCode();
      String body = response.body();
      if (status == 401 || status == 403) {
        throw new InstagramAuthException(opName + " auth failed: status=" + status);
      }
      if (status == 429) {
        throw new InstagramQuotaException(opName + " quota exceeded");
      }
      if (status / 100 != 2) {
        log.warn("{} failed: status={} body={}", opName, status, SensitiveLog.maskTokens(body));
        return null;
      }
      return http.parse(body);
    } catch (InstagramAuthException | InstagramQuotaException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      log.warn("{} IO error", opName, e);
      return null;
    }
  }

  private String caption(PublishContext context) {
    StringBuilder sb = new StringBuilder();
    String title = context.target().getPlatformTitle();
    if (title != null && !title.isBlank()) {
      sb.append(title.trim());
    }
    String description = context.target().getPlatformDescription();
    if (description != null && !description.isBlank()) {
      if (!sb.isEmpty()) sb.append("\n\n");
      sb.append(description.trim());
    }
    if (context.hashtags() != null && !context.hashtags().isEmpty()) {
      String tags =
          context.hashtags().stream()
              .filter(s -> s != null && !s.isBlank())
              .map(s -> s.startsWith("#") ? s : "#" + s)
              .collect(Collectors.joining(" "));
      if (!tags.isBlank()) {
        if (!sb.isEmpty()) sb.append("\n\n");
        sb.append(tags);
      }
    }
    if (sb.isEmpty()) return null;
    return PublisherHttp.limit(sb.toString(), 2200);
  }

  private String graphBase() {
    ConnectorProperties.Provider ig = connectorProperties.instagram();
    if (ig != null && ig.apiBaseUrl() != null && !ig.apiBaseUrl().isBlank()) {
      return trimTrailingSlash(ig.apiBaseUrl());
    }
    return DEFAULT_GRAPH_BASE;
  }

  private static String form(Map<String, String> values) {
    return values.entrySet().stream()
        .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String encPath(String value) {
    return enc(value).replace("+", "%20");
  }

  private static String trimTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static boolean isPublicHttpUrl(String value) {
    if (value == null || value.isBlank()) return false;
    URI uri = URI.create(value);
    String scheme = uri.getScheme();
    String host = uri.getHost();
    return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
        && host != null
        && !"localhost".equalsIgnoreCase(host)
        && !"127.0.0.1".equals(host);
  }

  private static class InstagramAuthException extends RuntimeException {
    InstagramAuthException(String message) {
      super(message);
    }
  }

  private static class InstagramQuotaException extends RuntimeException {
    InstagramQuotaException(String message) {
      super(message);
    }
  }
}
