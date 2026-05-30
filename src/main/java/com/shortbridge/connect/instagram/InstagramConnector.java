package com.shortbridge.connect.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.common.logging.SensitiveLog;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ConnectorProperties.class)
public class InstagramConnector implements SocialConnector {

  private static final String AUTH_URL = "https://www.facebook.com/v23.0/dialog/oauth";
  private static final String DEFAULT_GRAPH_BASE = "https://graph.facebook.com/v23.0";
  private static final String BUSINESS_LOGIN_EXTRAS = "{\"setup\":{\"channel\":\"IG_API_ONBOARDING\"}}";
  private static final String PAGE_LOOKUP_FIELDS =
      "id,name,access_token,instagram_business_account{id,username,name},connected_instagram_account{id,username,name}";
  private static final List<String> DEFAULT_SCOPES =
      List.of("pages_show_list", "pages_read_engagement", "instagram_basic", "instagram_content_publish");

  private final ConnectorProperties properties;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public Platform platform() {
    return Platform.INSTAGRAM;
  }

  @Override
  public String authorizationUrl(AuthorizationContext ctx) {
    return AUTH_URL
        + "?client_id=" + enc(requireClientId())
        + "&display=page"
        + "&extras=" + enc(BUSINESS_LOGIN_EXTRAS)
        + "&redirect_uri=" + enc(ctx.redirectUri())
        + "&scope=" + enc(scopeCsv())
        + "&response_type=code"
        + "&state=" + enc(ctx.state());
  }

  @Override
  public ConnectionResult handleCallback(CallbackContext ctx) {
    JsonNode shortToken = exchangeCodeForToken(ctx);
    String accessToken = shortToken.path("access_token").asText(null);
    if (isBlank(accessToken)) {
      throw ShortBridgeException.of(ErrorCode.EXTERNAL_PLATFORM_ERROR, "Instagram token exchange returned no token");
    }

    JsonNode longToken = exchangeForLongLivedToken(accessToken);
    String userToken = longToken.path("access_token").asText(accessToken);
    long expiresIn = longToken.path("expires_in").asLong(0);
    Instant expiresAt = expiresIn > 0 ? Instant.now().plusSeconds(expiresIn) : null;

    PageInstagramAccount page = findConnectedInstagramPage(userToken);
    String rawJson = writeRawProfile(page, expiresIn);

    return new ConnectionResult(
        page.instagramUserId(),
        page.instagramUsername(),
        page.pageAccessToken(),
        null,
        expiresAt,
        scopeCsv(),
        rawJson);
  }

  private JsonNode exchangeCodeForToken(CallbackContext ctx) {
    Map<String, String> body =
        Map.of(
            "client_id", requireClientId(),
            "client_secret", requireClientSecret(),
            "redirect_uri", ctx.redirectUri(),
            "code", ctx.code());
    return postForm(graphBase() + "/oauth/access_token", body, "instagram token exchange");
  }

  private JsonNode exchangeForLongLivedToken(String accessToken) {
    String url =
        graphBase()
            + "/oauth/access_token?grant_type=fb_exchange_token"
            + "&client_id=" + enc(requireClientId())
            + "&client_secret=" + enc(requireClientSecret())
            + "&fb_exchange_token=" + enc(accessToken);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            .GET()
            .build();
    return sendForJson(request, "instagram long-lived token exchange");
  }

  private PageInstagramAccount findConnectedInstagramPage(String userToken) {
    String url =
        graphBase()
            + "/me/accounts?fields=" + enc(PAGE_LOOKUP_FIELDS)
            + "&access_token=" + enc(userToken);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            .GET()
            .build();
    JsonNode root = sendForJson(request, "instagram page lookup");
    JsonNode data = root.path("data");
    if (data.isArray()) {
      for (JsonNode page : data) {
        JsonNode ig = page.path("instagram_business_account");
        if (ig.isMissingNode() || ig.path("id").isMissingNode() || isBlank(ig.path("id").asText(null))) {
          ig = page.path("connected_instagram_account");
        }
        String igId = ig.path("id").asText(null);
        String pageAccessToken = page.path("access_token").asText(null);
        if (!isBlank(igId) && !isBlank(pageAccessToken)) {
          String username = ig.path("username").asText(ig.path("name").asText("Instagram"));
          return new PageInstagramAccount(
              page.path("id").asText(null),
              page.path("name").asText(null),
              pageAccessToken,
              igId,
              username);
        }
      }
    }
    log.warn("instagram page lookup returned no connected IG account: body={}", maskedJson(root));
    throw ShortBridgeException.of(
        ErrorCode.EXTERNAL_PLATFORM_ERROR,
        "No Instagram professional account is connected to an accessible Facebook Page");
  }

  private JsonNode postForm(String url, Map<String, String> form, String opName) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(form(form)))
            .build();
    return sendForJson(request, opName);
  }

  private JsonNode sendForJson(HttpRequest request, String opName) {
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        log.warn(
            "{} failed: status={} body={}",
            opName,
            response.statusCode(),
            SensitiveLog.maskTokens(response.body()));
        throw ShortBridgeException.of(ErrorCode.EXTERNAL_PLATFORM_ERROR, opName + ": " + response.statusCode());
      }
      return mapper.readTree(response.body());
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw ShortBridgeException.of(ErrorCode.EXTERNAL_PLATFORM_ERROR, e, opName + " IO");
    }
  }

  private String writeRawProfile(PageInstagramAccount page, long expiresIn) {
    try {
      return mapper.writeValueAsString(
          Map.of(
              "pageId", nullToEmpty(page.pageId()),
              "pageName", nullToEmpty(page.pageName()),
              "instagramUserId", nullToEmpty(page.instagramUserId()),
              "instagramUsername", nullToEmpty(page.instagramUsername()),
              "tokenExpiresIn", expiresIn));
    } catch (IOException e) {
      return null;
    }
  }

  private String maskedJson(JsonNode node) {
    try {
      return SensitiveLog.maskTokens(mapper.writeValueAsString(node));
    } catch (IOException e) {
      return "<unserializable>";
    }
  }

  private String graphBase() {
    ConnectorProperties.Provider ig = properties.instagram();
    if (ig != null && !isBlank(ig.apiBaseUrl())) {
      return trimTrailingSlash(ig.apiBaseUrl());
    }
    return DEFAULT_GRAPH_BASE;
  }

  private String scopeCsv() {
    ConnectorProperties.Provider ig = properties.instagram();
    List<String> scopes = ig == null || ig.scopes() == null || ig.scopes().isEmpty() ? DEFAULT_SCOPES : ig.scopes();
    return scopes.stream().filter(s -> !isBlank(s)).collect(Collectors.joining(","));
  }

  private String requireClientId() {
    ConnectorProperties.Provider ig = properties.instagram();
    if (ig == null || isBlank(ig.clientId())) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "instagram client-id not configured");
    }
    return ig.clientId();
  }

  private String requireClientSecret() {
    ConnectorProperties.Provider ig = properties.instagram();
    if (ig == null || isBlank(ig.clientSecret())) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "instagram client-secret not configured");
    }
    return ig.clientSecret();
  }

  private static String form(Map<String, String> values) {
    return values.entrySet().stream()
        .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static boolean isBlank(String value) {
    return value == null
        || value.isBlank()
        || "REPLACE_ME".equals(value)
        || "not-set".equals(value)
        || "not-set-yet".equals(value);
  }

  private static String trimTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private record PageInstagramAccount(
      String pageId,
      String pageName,
      String pageAccessToken,
      String instagramUserId,
      String instagramUsername) {}
}
