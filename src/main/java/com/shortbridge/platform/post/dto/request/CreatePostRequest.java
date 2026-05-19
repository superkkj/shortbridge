package com.shortbridge.platform.post.dto.request;

import com.shortbridge.platform.post.domain.PublishMode;
import com.shortbridge.platform.post.dto.command.CreatePostCommand;
import com.shortbridge.platform.socialaccount.domain.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreatePostRequest(
    @NotNull UUID videoId,
    @NotBlank @Size(max = 255) String title,
    @Size(max = 5000) String description,
    @Size(max = 30) List<@Size(max = 100) String> hashtags,
    @NotNull PublishMode publishMode,
    Instant scheduledAt,
    @NotEmpty List<Platform> targets) {

  public CreatePostCommand toCommand(UUID userId) {
    return new CreatePostCommand(userId, videoId, title, description, hashtags, publishMode, scheduledAt, targets);
  }
}
