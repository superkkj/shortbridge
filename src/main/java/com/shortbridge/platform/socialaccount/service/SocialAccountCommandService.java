package com.shortbridge.platform.socialaccount.service;

import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.repository.SocialAccountRepository;
import com.shortbridge.support.security.token.PlatformTokenCipher;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SocialAccountCommandService {

  private final SocialAccountRepository socialAccountRepository;
  private final PlatformTokenCipher tokenCipher;

  public SocialAccount upsertConnection(
      UUID userId,
      Platform platform,
      String platformUserId,
      String displayName,
      String accessToken,
      String refreshToken,
      Instant expiresAt,
      String scopes,
      String rawProfileJson) {
    SocialAccount existing =
        socialAccountRepository
            .findByUserIdAndPlatformAndStatus(userId, platform, com.shortbridge.platform.socialaccount.domain.SocialAccountStatus.CONNECTED)
            .orElse(null);

    String encAccess = tokenCipher.encrypt(accessToken);
    String encRefresh = tokenCipher.encrypt(refreshToken);

    if (existing != null) {
      existing.updateToken(encAccess, encRefresh, tokenCipher.activeVersion(), expiresAt);
      return existing;
    }

    SocialAccount account =
        SocialAccount.builder()
            .userId(userId)
            .platform(platform)
            .platformUserId(platformUserId)
            .displayName(displayName)
            .accessTokenEncrypted(encAccess)
            .refreshTokenEncrypted(encRefresh)
            .tokenKeyVersion(tokenCipher.activeVersion())
            .tokenExpiresAt(expiresAt)
            .scopes(scopes)
            .rawProfileJson(rawProfileJson)
            .build();
    return socialAccountRepository.save(account);
  }

  public void disconnect(SocialAccount account) {
    account.disconnect();
  }
}
