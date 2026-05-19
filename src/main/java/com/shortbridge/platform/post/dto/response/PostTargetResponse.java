package com.shortbridge.platform.post.dto.response;

import com.shortbridge.common.util.JsonUtils;
import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import com.shortbridge.platform.socialaccount.domain.Platform;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostTargetResponse(
    UUID id,
    Platform platform,
    UUID socialAccountId,
    String platformTitle,
    String platformDescription,
    List<String> platformHashtags,
    PostTargetStatus status,
    String externalPostId,
    String externalPublishId,
    String errorCode,
    String errorMessage,
    int retryCount,
    Instant scheduledAt,
    Instant publishedAt) {

  public static PostTargetResponse from(PostTarget target) {
    return new PostTargetResponse(
        target.getId(),
        target.getPlatform(),
        target.getSocialAccountId(),
        target.getPlatformTitle(),
        target.getPlatformDescription(),
        JsonUtils.splitHashtags(target.getPlatformHashtags()),
        target.getStatus(),
        target.getExternalPostId(),
        target.getExternalPublishId(),
        target.getErrorCode(),
        target.getErrorMessage(),
        target.getRetryCount(),
        target.getScheduledAt(),
        target.getPublishedAt());
  }
}
