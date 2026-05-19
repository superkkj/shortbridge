package com.shortbridge.connect.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.connect.connector.AuthorizationContext;
import com.shortbridge.connect.connector.CallbackContext;
import com.shortbridge.connect.connector.ConnectionResult;
import com.shortbridge.connect.connector.ConnectorProperties;
import com.shortbridge.connect.connector.SocialConnector;
import com.shortbridge.platform.socialaccount.domain.Platform;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ConnectorProperties.class)
public class YouTubeConnector implements SocialConnector {

  private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
  private static final String CHANNELS_URL =
      "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true";
  private static final String SCOPES =
      "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly";

  private final ConnectorProperties properties;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Platform platform() {
    return Platform.YOUTUBE;
  }

  @Override
  public String authorizationUrl(AuthorizationContext ctx) {
    String clientId = requireClientId();
    return AUTH_URL
        + "?response_type=code"
        + "&client_id=" + enc(clientId)
        + "&redirect_uri=" + enc(ctx.redirectUri())
        + "&scope=" + enc(SCOPES)
        + "&access_type=offline"
        + "&include_granted_scopes=true"
        + "&prompt=consent"
        + "&state=" + enc(ctx.state());
  }

  @Override
  public ConnectionResult handleCallback(CallbackContext ctx) {
    Map<String, Object> token = exchangeCodeForToken(ctx);
    String accessToken = (String) token.get("access_token");
    String refreshToken = (String) token.get("refresh_token");
    Integer expiresInSec = ((Number) token.getOrDefault("expires_in", 0)).intValue();
    Instant expiresAt = expiresInSec > 0 ? Instant.now().plusSeconds(expiresInSec) : null;
    String scope = String.valueOf(token.getOrDefault("scope", ""));

    JsonNode channel = fetchChannel(accessToken);
    String channelId = "self";
    String displayName = null;
    if (channel != null) {
      channelId = channel.path("id").asText("self");
      displayName = channel.path("snippet").path("title").asText(null);
    }

    String rawJson;
    try {
      rawJson = mapper.writeValueAsString(token);
    } catch (IOException e) {
      rawJson = null;
    }
    return new ConnectionResult(
        channelId, displayName, accessToken, refreshToken, expiresAt, scope, rawJson);
  }

  private Map<String, Object> exchangeCodeForToken(CallbackContext ctx) {
    String clientId = requireClientId();
    String clientSecret = requireClientSecret();
    String body =
        "code=" + enc(ctx.code())
            + "&client_id=" + enc(clientId)
            + "&client_secret=" + enc(clientSecret)
            + "&redirect_uri=" + enc(ctx.redirectUri())
            + "&grant_type=authorization_code";
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        log.warn("youtube token exchange failed: status={} body={}", response.statusCode(), response.body());
        throw ShortBridgeException.of(ErrorCode.EXTERNAL_PLATFORM_ERROR, "YouTube token exchange: " + response.statusCode());
      }
      JsonNode json = mapper.readTree(response.body());
      Map<String, Object> map = mapper.convertValue(json, Map.class);
      return map;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ShortBridgeException.of(ErrorCode.EXTERNAL_PLATFORM_ERROR, e, "YouTube token exchange IO");
    }
  }

  private JsonNode fetchChannel(String accessToken) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(CHANNELS_URL))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .GET()
            .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        log.warn("youtube channels fetch failed: status={} body={}", response.statusCode(), response.body());
        return null;
      }
      JsonNode root = mapper.readTree(response.body());
      JsonNode items = root.path("items");
      if (items.isArray() && items.size() > 0) {
        return items.get(0);
      }
      return null;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("youtube channels fetch IO error", e);
      return null;
    }
  }

  private String requireClientId() {
    if (properties.youtube() == null || isBlank(properties.youtube().clientId())) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "youtube client-id not configured");
    }
    return properties.youtube().clientId();
  }

  private String requireClientSecret() {
    if (properties.youtube() == null || isBlank(properties.youtube().clientSecret())) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "youtube client-secret not configured");
    }
    return properties.youtube().clientSecret();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank() || "not-set-yet".equals(s);
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
