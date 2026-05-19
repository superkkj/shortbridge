package com.shortbridge.platform.socialaccount.service;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.domain.SocialAccountStatus;
import com.shortbridge.platform.socialaccount.repository.SocialAccountRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialAccountQueryService {

  private final SocialAccountRepository socialAccountRepository;

  public SocialAccount get(UUID userId, UUID id) {
    return socialAccountRepository
        .findOne(id, userId)
        .orElseThrow(() -> ShortBridgeException.of(ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND, id));
  }

  public List<SocialAccount> list(UUID userId) {
    return socialAccountRepository.findByUserIdOrderByPlatformAsc(userId);
  }

  public Map<UUID, SocialAccount> findMapByIds(UUID userId, Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) return Map.of();
    return socialAccountRepository.findAllByUserIdAndIds(userId, ids).stream()
        .collect(Collectors.toMap(SocialAccount::getId, Function.identity()));
  }

  public SocialAccount getConnected(UUID userId, Platform platform) {
    return socialAccountRepository
        .findByUserIdAndPlatformAndStatus(userId, platform, SocialAccountStatus.CONNECTED)
        .orElseThrow(() -> ShortBridgeException.of(ErrorCode.SOCIAL_ACCOUNT_NOT_CONNECTED, platform));
  }

  public void validateConnectedPlatforms(UUID userId, List<Platform> platforms) {
    for (Platform platform : platforms) {
      getConnected(userId, platform);
    }
  }
}
