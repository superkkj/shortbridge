package com.shortbridge.publish.dto;

import com.shortbridge.platform.socialaccount.domain.Platform;
import java.util.UUID;

public record EnqueueRequest(UUID postTargetId, UUID postId, UUID userId, Platform platform, int attempt) {

  public static EnqueueRequest of(UUID postTargetId, UUID postId, UUID userId, Platform platform) {
    return new EnqueueRequest(postTargetId, postId, userId, platform, 1);
  }
}
