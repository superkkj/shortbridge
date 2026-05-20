package com.shortbridge.platform.socialaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Platform.queueName() — single source of truth")
class PlatformTest {

  @Test
  void youtube_queueName() {
    assertThat(Platform.YOUTUBE.queueName()).isEqualTo("publish.youtube");
  }

  @Test
  void instagram_queueName() {
    assertThat(Platform.INSTAGRAM.queueName()).isEqualTo("publish.instagram");
  }

  @Test
  void tiktok_queueName() {
    assertThat(Platform.TIKTOK.queueName()).isEqualTo("publish.tiktok");
  }

  @Test
  @DisplayName("queueName 은 'publish.' + 소문자 enum name 규칙을 따른다 — 새 platform 자동 적용")
  void queueName_followsConvention() {
    for (Platform p : Platform.values()) {
      assertThat(p.queueName()).isEqualTo("publish." + p.name().toLowerCase());
    }
  }
}
