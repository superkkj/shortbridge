package com.shortbridge.publish.publisher;

import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.publish.dto.PublishOutcome;

public interface SocialPublisher {

  Platform platform();

  PublishOutcome publish(PublishContext context);
}
