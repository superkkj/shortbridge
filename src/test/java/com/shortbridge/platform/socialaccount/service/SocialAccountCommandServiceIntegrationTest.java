package com.shortbridge.platform.socialaccount.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.domain.SocialAccountStatus;
import com.shortbridge.platform.socialaccount.repository.SocialAccountRepository;
import com.shortbridge.platform.user.domain.User;
import com.shortbridge.platform.user.repository.UserRepository;
import com.shortbridge.support.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("SocialAccountCommandService 통합")
class SocialAccountCommandServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired SocialAccountCommandService commandService;
  @Autowired SocialAccountRepository socialAccountRepository;
  @Autowired UserRepository userRepository;

  private User user;

  @BeforeEach
  void seed() {
    socialAccountRepository.deleteAll();
    userRepository.deleteAll();
    user = userRepository.save(User.builder().email("social@example.com").displayName("Social").build());
  }

  @Test
  @DisplayName("upsertConnection — RECONNECT_REQUIRED 같은 플랫폼 계정이면 새 row 대신 기존 row를 CONNECTED로 갱신")
  void upsertConnection_reconnectRequiredSamePlatformAccount_updatesExisting() {
    SocialAccount existing =
        SocialAccount.builder()
            .userId(user.getId())
            .platform(Platform.YOUTUBE)
            .platformUserId("yt-1")
            .displayName("Old")
            .build();
    existing.markReconnectRequired();
    existing = socialAccountRepository.save(existing);

    SocialAccount updated =
        commandService.upsertConnection(
            user.getId(),
            Platform.YOUTUBE,
            "yt-1",
            "New",
            "access-token",
            "refresh-token",
            Instant.now().plusSeconds(3600),
            "scope",
            "{}");

    assertThat(updated.getId()).isEqualTo(existing.getId());
    assertThat(updated.getStatus()).isEqualTo(SocialAccountStatus.CONNECTED);
    assertThat(updated.getDisplayName()).isEqualTo("New");
    assertThat(updated.getAccessTokenEncrypted()).isNotBlank();
    assertThat(socialAccountRepository.findByUserIdOrderByPlatformAsc(user.getId())).hasSize(1);
  }
}
