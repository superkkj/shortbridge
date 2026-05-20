package com.shortbridge.publish.instagram;

import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.publish.dto.PublishOutcome;
import com.shortbridge.publish.publisher.PublishContext;
import com.shortbridge.publish.publisher.SocialPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InstagramPublisher implements SocialPublisher {

  @Override
  public Platform platform() {
    return Platform.INSTAGRAM;
  }

  @Override
  public PublishOutcome publish(PublishContext context) {
    // Implementation pending: Graph API (POST /{ig-user-id}/media → container status polling →
    // POST /{ig-user-id}/media_publish). Blocked on Meta App Review + Facebook Page link.
    // Until then we refuse the request rather than fake a success — feature flag should also be off.
    log.warn(
        "instagram publish requested but not implemented: postTargetId={}",
        context.target().getId());
    return PublishOutcome.blockedByCapability("Instagram publish is not implemented yet");
  }
}
