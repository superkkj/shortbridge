package com.shortbridge.connect.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.shortbridge.platform.socialaccount.domain.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConnectorProperties (type-safe Platform mapping)")
class ConnectorPropertiesTest {

  private static ConnectorProperties.Provider provider(String redirect) {
    return new ConnectorProperties.Provider("client-id", "client-secret", redirect);
  }

  @Test
  void providerFor_returnsConfiguredProvider() {
    ConnectorProperties props =
        new ConnectorProperties(
            "http://localhost:8080",
            provider("https://yt.example.com/cb"),
            provider("https://tt.example.com/cb"),
            provider(null));

    assertThat(props.providerFor(Platform.YOUTUBE).redirectUri()).isEqualTo("https://yt.example.com/cb");
    assertThat(props.providerFor(Platform.TIKTOK).redirectUri()).isEqualTo("https://tt.example.com/cb");
    assertThat(props.providerFor(Platform.INSTAGRAM).redirectUri()).isNull();
  }

  @Test
  void redirectUri_explicitProviderUri_takesPrecedence() {
    ConnectorProperties props =
        new ConnectorProperties(
            "http://localhost:8080",
            provider("https://override.example.com/yt"),
            provider(null),
            provider(null));

    assertThat(props.redirectUri(Platform.YOUTUBE)).isEqualTo("https://override.example.com/yt");
  }

  @Test
  void redirectUri_noProviderUri_fallsBackToBaseUrl() {
    ConnectorProperties props =
        new ConnectorProperties(
            "https://shortbridge.example.com",
            provider(null),
            provider(""),
            provider("   "));

    assertThat(props.redirectUri(Platform.YOUTUBE))
        .isEqualTo("https://shortbridge.example.com/connect/youtube/callback");
    assertThat(props.redirectUri(Platform.TIKTOK))
        .isEqualTo("https://shortbridge.example.com/connect/tiktok/callback");
    assertThat(props.redirectUri(Platform.INSTAGRAM))
        .isEqualTo("https://shortbridge.example.com/connect/instagram/callback");
  }

  @Test
  void redirectUri_blankBaseUrl_defaultsToLocalhost() {
    ConnectorProperties props =
        new ConnectorProperties("", provider(null), provider(null), provider(null));

    assertThat(props.redirectUri(Platform.YOUTUBE))
        .isEqualTo("http://localhost:8080/connect/youtube/callback");
  }

  @Test
  void redirectUri_nullProvider_fallsBackToBaseUrl() {
    ConnectorProperties props =
        new ConnectorProperties("https://x.com", null, null, null);

    assertThat(props.redirectUri(Platform.YOUTUBE))
        .isEqualTo("https://x.com/connect/youtube/callback");
  }
}
