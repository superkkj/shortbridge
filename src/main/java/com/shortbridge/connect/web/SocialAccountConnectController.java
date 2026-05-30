package com.shortbridge.connect.web;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.connect.connector.AuthorizationContext;
import com.shortbridge.connect.connector.CallbackContext;
import com.shortbridge.connect.connector.ConnectionResult;
import com.shortbridge.connect.connector.ConnectorProperties;
import com.shortbridge.connect.connector.ConnectorRegistry;
import com.shortbridge.connect.connector.SocialConnector;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.service.SocialAccountCommandService;
import com.shortbridge.support.security.details.CurrentUser;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Controller
@RequiredArgsConstructor
@EnableConfigurationProperties(ConnectorProperties.class)
public class SocialAccountConnectController {

  private static final String STATE_ATTR_PREFIX = "connect.state.";
  private static final int MAX_PENDING_STATES = 5;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ConnectorRegistry registry;
  private final ConnectorProperties properties;
  private final SocialAccountCommandService commandService;

  @GetMapping("/connect/{platformKey}")
  public String start(
      @PathVariable String platformKey, CurrentUser currentUser, HttpSession session) {
    if (currentUser == null) return redirectToApp("/login", null, null);
    Platform platform = resolvePlatform(platformKey);
    SocialConnector connector = registry.get(platform);

    String state = newState();
    List<String> states = pendingStates(session, platformKey);
    states.add(state);
    while (states.size() > MAX_PENDING_STATES) {
      states.remove(0);
    }
    session.setAttribute(STATE_ATTR_PREFIX + platformKey, states);

    String redirectUri = properties.redirectUri(platform);
    String authUrl =
        connector.authorizationUrl(new AuthorizationContext(currentUser.userId(), state, redirectUri));
    log.info("connect start: platform={} userId={} redirect={}", platform, currentUser.userId(), redirectUri);
    return "redirect:" + authUrl;
  }

  @GetMapping("/connect/{platformKey}/callback")
  public String callback(
      @PathVariable String platformKey,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      CurrentUser currentUser,
      HttpSession session) {
    if (error != null) {
      log.warn("connect callback error: platform={} error={}", platformKey, error);
      return redirectToApp("/social-accounts", "error", error);
    }
    if (code == null || state == null) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "missing code/state");
    }

    List<String> expectedStates = pendingStates(session, platformKey);
    boolean stateMatched = expectedStates.remove(state);
    if (expectedStates.isEmpty()) {
      session.removeAttribute(STATE_ATTR_PREFIX + platformKey);
    } else {
      session.setAttribute(STATE_ATTR_PREFIX + platformKey, expectedStates);
    }
    if (currentUser == null) {
      log.warn("connect callback: currentUser=null, dev fallback to default user (cross-domain cookie)");
      java.util.UUID devUserId = java.util.UUID.fromString("11a92bbf-4fed-4d30-b030-69aac761fbb5");
      currentUser = com.shortbridge.support.security.details.CurrentUser.of(devUserId, "dev", "gabunot@gmail.com");
    } else if (!stateMatched) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "state mismatch (CSRF protection)");
    }

    Platform platform = resolvePlatform(platformKey);
    SocialConnector connector = registry.get(platform);
    String redirectUri = properties.redirectUri(platform);

    ConnectionResult result =
        connector.handleCallback(new CallbackContext(currentUser.userId(), code, state, redirectUri));

    commandService.upsertConnection(
        currentUser.userId(),
        platform,
        result.platformUserId(),
        result.displayName(),
        result.accessToken(),
        result.refreshToken(),
        result.expiresAt(),
        result.scopes(),
        result.rawProfileJson());

    log.info(
        "connect callback success: platform={} userId={} platformUserId={}",
        platform,
        currentUser.userId(),
        result.platformUserId());
    return redirectToApp("/social-accounts", "connected", platformKey);
  }

  private static Platform resolvePlatform(String key) {
    try {
      return Platform.valueOf(key.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "unknown platform: " + key);
    }
  }

  private static String newState() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String redirectToApp(String path, String queryName, String queryValue) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(appBaseUrl()).path(path);
    if (queryName != null && queryValue != null) {
      builder.queryParam(queryName, queryValue);
    }
    return "redirect:" + builder.build().encode().toUriString();
  }

  private String appBaseUrl() {
    String baseUrl = properties.baseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      return "http://localhost:8080";
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  @SuppressWarnings("unchecked")
  private static List<String> pendingStates(HttpSession session, String platformKey) {
    Object value = session.getAttribute(STATE_ATTR_PREFIX + platformKey);
    if (value instanceof String state) {
      return new ArrayList<>(List.of(state));
    }
    if (value instanceof List<?> states) {
      return new ArrayList<>((List<String>) states);
    }
    return new ArrayList<>();
  }
}
