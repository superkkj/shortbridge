package com.shortbridge.platform.post.dto.command;

import com.shortbridge.platform.post.domain.Post;
import com.shortbridge.platform.post.domain.PublishMode;
import com.shortbridge.platform.socialaccount.domain.Platform;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreatePostCommand(
    UUID userId,
    UUID videoId,
    String title,
    String description,
    List<String> hashtags,
    PublishMode publishMode,
    Instant scheduledAt,
    List<Platform> targets) {

  public Post toEntity() {
    return Post.builder()
        .userId(userId)
        .videoId(videoId)
        .title(title)
        .description(description)
        .hashtags(com.shortbridge.common.util.JsonUtils.joinHashtags(hashtags))
        .publishMode(publishMode)
        .scheduledAt(scheduledAt)
        .build();
  }
}
