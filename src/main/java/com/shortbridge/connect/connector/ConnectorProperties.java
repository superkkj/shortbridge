package com.shortbridge.connect.connector;

import com.shortbridge.platform.socialaccount.domain.Platform;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortbridge.connect")
public record ConnectorProperties(
    String baseUrl,
    Provider youtube,
    Provider tiktok,
    Provider instagram) {

  public record Provider(
      String clientId,
      String clientSecret,
      String redirectUri,
      String apiBaseUrl,
      List<String> scopes) {}

  /**
   * Provider 객체 매핑. Platform enum switch 는 exhaustiveness check 를 컴파일러가 강제하므로
   * 새 platform 추가 시 컴파일 에러로 누락을 잡아낸다.
   */
  public Provider providerFor(Platform platform) {
    return switch (platform) {
      case YOUTUBE -> youtube;
      case TIKTOK -> tiktok;
      case INSTAGRAM -> instagram;
    };
  }

  public String redirectUri(Platform platform) {
    Provider p = providerFor(platform);
    if (p != null && p.redirectUri() != null && !p.redirectUri().isBlank()) {
      return p.redirectUri();
    }
    String base = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl;
    return base + "/connect/" + platform.name().toLowerCase() + "/callback";
  }
}
