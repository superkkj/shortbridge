package com.shortbridge.platform.post.dto.response;

import com.shortbridge.common.util.JsonUtils;
import com.shortbridge.platform.post.domain.Post;
import com.shortbridge.platform.post.domain.PostStatus;
import com.shortbridge.platform.post.domain.PublishMode;
import com.shortbridge.platform.posttarget.domain.PostTarget;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostResponse(
    UUID id,
    UUID videoId,
    String title,
    String description,
    List<String> hashtags,
    PublishMode publishMode,
    Instant scheduledAt,
    PostStatus status,
    Instant createdAt,
    List<PostTargetResponse> targets) {

  public static PostResponse from(Post post, List<PostTarget> targets) {
    return new PostResponse(
        post.getId(),
        post.getVideoId(),
        post.getTitle(),
        post.getDescription(),
        JsonUtils.splitHashtags(post.getHashtags()),
        post.getPublishMode(),
        post.getScheduledAt(),
        post.getStatus(),
        post.getCreatedAt(),
        targets.stream().map(PostTargetResponse::from).toList());
  }
}
