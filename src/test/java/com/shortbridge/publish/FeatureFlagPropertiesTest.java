package com.shortbridge.publish;

import static org.assertj.core.api.Assertions.assertThat;

import com.shortbridge.platform.socialaccount.domain.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FeatureFlagProperties.enabled(Platform)")
class FeatureFlagPropertiesTest {

  @Test
  void allEnabled() {
    FeatureFlagProperties flags = new FeatureFlagProperties(true, true, true);

    assertThat(flags.enabled(Platform.YOUTUBE)).isTrue();
    assertThat(flags.enabled(Platform.INSTAGRAM)).isTrue();
    assertThat(flags.enabled(Platform.TIKTOK)).isTrue();
  }

  @Test
  void allDisabled() {
    FeatureFlagProperties flags = new FeatureFlagProperties(false, false, false);

    assertThat(flags.enabled(Platform.YOUTUBE)).isFalse();
    assertThat(flags.enabled(Platform.INSTAGRAM)).isFalse();
    assertThat(flags.enabled(Platform.TIKTOK)).isFalse();
  }

  @Test
  void perPlatformIndependence() {
    FeatureFlagProperties flags = new FeatureFlagProperties(true, false, true);

    assertThat(flags.enabled(Platform.YOUTUBE)).isTrue();
    assertThat(flags.enabled(Platform.INSTAGRAM)).isFalse();
    assertThat(flags.enabled(Platform.TIKTOK)).isTrue();
  }
}
