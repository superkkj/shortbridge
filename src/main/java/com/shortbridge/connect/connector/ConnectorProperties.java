package com.shortbridge.connect.connector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortbridge.connect")
public record ConnectorProperties(
    String baseUrl,
    Provider youtube,
    Provider tiktok,
    Provider instagram) {

  public record Provider(String clientId, String clientSecret, String redirectUri) {}

  public String redirectUri(String platformPath) {
    Provider p = switch (platformPath) {
      case "youtube" -> youtube;
      case "tiktok" -> tiktok;
      case "instagram" -> instagram;
      default -> null;
    };
    if (p != null && p.redirectUri() != null && !p.redirectUri().isBlank()) {
      return p.redirectUri();
    }
    String base = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8080" : baseUrl;
    return base + "/connect/" + platformPath + "/callback";
  }
}
