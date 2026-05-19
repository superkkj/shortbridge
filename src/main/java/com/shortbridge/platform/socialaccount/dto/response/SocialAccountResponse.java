package com.shortbridge.platform.socialaccount.dto.response;

import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.domain.SocialAccountStatus;
import java.time.Instant;
import java.util.UUID;

public record SocialAccountResponse(
    UUID id,
    Platform platform,
    String platformUserId,
    String displayName,
    SocialAccountStatus status,
    Instant tokenExpiresAt,
    Instant disconnectedAt) {

  public static SocialAccountResponse from(SocialAccount account) {
    return new SocialAccountResponse(
        account.getId(),
        account.getPlatform(),
        account.getPlatformUserId(),
        account.getDisplayName(),
        account.getStatus(),
        account.getTokenExpiresAt(),
        account.getDisconnectedAt());
  }
}
