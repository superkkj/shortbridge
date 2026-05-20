package com.shortbridge.connect.tiktok;

import com.fasterxml.jackson.core.type.TypeReference;
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
public class TikTokConnector implements SocialConnector {

  private static final String AUTH_URL = "https://www.tiktok.com/v2/auth/authorize/";
  private static final String TOKEN_URL = "https://open.tiktokapis.com/v2/oauth/token/";
  private static final String USERINFO_URL =
      "https://open.tiktokapis.com/v2/user/info/?fields=open_id,union_id,avatar_url,display_name";
  private static final String SCOPES = "user.info.basic,video.publish,video.upload";

  private final ConnectorProperties properties;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Platform platform() {
    return Platform.TIKTOK;
  }

  @Override
  public String authorizationUrl(AuthorizationContext ctx) {
    String clientKey = requireClientKey();
    return AUTH_URL
        + "?client_key=" + enc(clientKey)
        + "&response_type=code"
        + "&scope=" + enc(SCOPES)
        + "&redirect_uri=" + enc(ctx.redirectUri())
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
    String openId = String.valueOf(token.getOrDefault("open_id", "self"));

    String displayName = fetchDisplayName(accessToken);

    String rawJson;
    try {
      rawJson = mapper.writeValueAsString(token);
    } catch (IOException e) {
      rawJson = null;
    }
    return new ConnectionResult(
        openId, displayName, accessToken, refreshToken, expiresAt, scope, rawJson);
  }

  private Map<String, Object> exchangeCodeForToken(CallbackContext ctx) {
    String clientKey = requireClientKey();
    String clientSecret = requireClientSecret();
    String body =
        "client_key=" + enc(clientKey)
            + "&client_secret=" + enc(clientSecret)
            + "&code=" + enc(ctx.code())
            + "&grant_type=authorization_code"
            + "&redirect_uri=" + enc(ctx.redirectUri());
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        log.warn("tiktok token exchange failed: status={} body={}", response.statusCode(), response.body());
        throw ShortBridgeException.of(
            ErrorCode.EXTERNAL_PLATFORM_ERROR, "TikTok token exchange: " + response.statusCode());
      }
      JsonNode json = mapper.readTree(response.body());
      return mapper.convertValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ShortBridgeException.of(ErrorCode.EXTERNAL_PLATFORM_ERROR, e, "TikTok token exchange IO");
    }
  }

  private String fetchDisplayName(String accessToken) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(USERINFO_URL))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .GET()
            .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        log.warn("tiktok user info failed: status={} body={}", response.statusCode(), response.body());
        return null;
      }
      JsonNode root = mapper.readTree(response.body());
      return root.path("data").path("user").path("display_name").asText(null);
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("tiktok user info IO error", e);
      return null;
    }
  }

  private String requireClientKey() {
    if (properties.tiktok() == null || isBlank(properties.tiktok().clientId())) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "tiktok client-key not configured");
    }
    return properties.tiktok().clientId();
  }

  private String requireClientSecret() {
    if (properties.tiktok() == null || isBlank(properties.tiktok().clientSecret())) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "tiktok client-secret not configured");
    }
    return properties.tiktok().clientSecret();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank() || "not-set-yet".equals(s);
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
