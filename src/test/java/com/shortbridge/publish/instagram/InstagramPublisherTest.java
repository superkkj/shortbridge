package com.shortbridge.publish.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.publish.dto.PublishOutcome;
import com.shortbridge.publish.publisher.PublishContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InstagramPublisher (stub 안전성)")
class InstagramPublisherTest {

  @Test
  @DisplayName("Platform 은 INSTAGRAM 을 반환한다 (Registry 자동 등록 키)")
  void platform_returnsInstagram() {
    InstagramPublisher publisher = new InstagramPublisher();
    assertThat(publisher.platform()).isEqualTo(Platform.INSTAGRAM);
  }

  @Test
  @DisplayName("publish 는 가짜 success 가 아닌 BLOCKED_BY_CAPABILITY 를 반환한다 (실수 발행 방지)")
  void publish_returnsBlockedByCapability() {
    InstagramPublisher publisher = new InstagramPublisher();
    PostTarget target =
        PostTarget.builder()
            .userId(UUID.randomUUID())
            .postId(UUID.randomUUID())
            .socialAccountId(UUID.randomUUID())
            .platform(Platform.INSTAGRAM)
            .platformTitle("title")
            .initialStatus(PostTargetStatus.READY)
            .idempotencyKey("k")
            .build();
    PublishContext context = new PublishContext(target, null, null, List.of());

    PublishOutcome outcome = publisher.publish(context);

    assertThat(outcome.resultType()).isEqualTo(PublishOutcome.ResultType.BLOCKED_BY_CAPABILITY);
    assertThat(outcome.externalPostId()).isNull();
    assertThat(outcome.errorMessage()).contains("not implemented");
  }
}
