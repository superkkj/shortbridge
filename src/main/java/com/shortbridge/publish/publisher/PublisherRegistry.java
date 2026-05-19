package com.shortbridge.publish.publisher;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.platform.socialaccount.domain.Platform;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PublisherRegistry {

  private final Map<Platform, SocialPublisher> publishers;

  public PublisherRegistry(List<SocialPublisher> publishers) {
    this.publishers =
        publishers.stream().collect(Collectors.toMap(SocialPublisher::platform, Function.identity()));
  }

  public SocialPublisher get(Platform platform) {
    SocialPublisher publisher = publishers.get(platform);
    if (publisher == null) {
      throw ShortBridgeException.of(ErrorCode.EXTERNAL_PLATFORM_ERROR, "no publisher for " + platform);
    }
    return publisher;
  }
}
