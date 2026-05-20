package com.shortbridge.support.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import com.shortbridge.platform.socialaccount.domain.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QueueNames")
class QueueNamesTest {

  @Test
  @DisplayName("상수는 Platform.queueName() 과 일치한다 (drift 방지)")
  void constants_matchPlatformQueueName() {
    assertThat(QueueNames.YOUTUBE).isEqualTo(Platform.YOUTUBE.queueName());
    assertThat(QueueNames.INSTAGRAM).isEqualTo(Platform.INSTAGRAM.queueName());
    assertThat(QueueNames.TIKTOK).isEqualTo(Platform.TIKTOK.queueName());
  }

  @Test
  @DisplayName("dlqOf 는 큐 이름에 .dlq 를 붙인다")
  void dlqOf_appendsSuffix() {
    assertThat(QueueNames.dlqOf("publish.youtube")).isEqualTo("publish.youtube.dlq");
  }
}
