package com.shortbridge.publish.dto;

import com.shortbridge.platform.socialaccount.domain.Platform;
import java.util.UUID;

public record PublishMessage(
    UUID postTargetId, UUID postId, UUID userId, Platform platform, int attempt, String idempotencyKey) {}
