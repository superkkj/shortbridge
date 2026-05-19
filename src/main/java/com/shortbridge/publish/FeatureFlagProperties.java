package com.shortbridge.publish;

import com.shortbridge.platform.socialaccount.domain.Platform;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortbridge.publish.feature-flags")
public record FeatureFlagProperties(
    boolean youtubeEnabled, boolean instagramEnabled, boolean tiktokEnabled) {

  public boolean enabled(Platform platform) {
    return switch (platform) {
      case YOUTUBE -> youtubeEnabled;
      case INSTAGRAM -> instagramEnabled;
      case TIKTOK -> tiktokEnabled;
    };
  }
}
