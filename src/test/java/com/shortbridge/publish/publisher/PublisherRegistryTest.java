package com.shortbridge.publish.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.publish.dto.PublishOutcome;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PublisherRegistry (전략 패턴 dispatch)")
class PublisherRegistryTest {

  @Test
  @DisplayName("주입된 모든 SocialPublisher 가 Platform 기준으로 Map 에 자동 등록된다")
  void registersAllPublishersByPlatform() {
    SocialPublisher yt = new FixedOutcomePublisher(Platform.YOUTUBE, "yt");
    SocialPublisher ig = new FixedOutcomePublisher(Platform.INSTAGRAM, "ig");
    SocialPublisher tt = new FixedOutcomePublisher(Platform.TIKTOK, "tt");

    PublisherRegistry registry = new PublisherRegistry(List.of(yt, ig, tt));

    assertThat(registry.get(Platform.YOUTUBE)).isSameAs(yt);
    assertThat(registry.get(Platform.INSTAGRAM)).isSameAs(ig);
    assertThat(registry.get(Platform.TIKTOK)).isSameAs(tt);
  }

  @Test
  @DisplayName("등록된 publisher 가 없는 Platform 을 요청하면 EXTERNAL_PLATFORM_ERROR 예외")
  void missingPublisher_throws() {
    PublisherRegistry registry = new PublisherRegistry(
        List.of(new FixedOutcomePublisher(Platform.YOUTUBE, "yt")));

    assertThatThrownBy(() -> registry.get(Platform.TIKTOK))
        .isInstanceOf(ShortBridgeException.class)
        .extracting(e -> ((ShortBridgeException) e).getErrorCode())
        .isEqualTo(ErrorCode.EXTERNAL_PLATFORM_ERROR);
  }

  @Test
  @DisplayName("dispatch 된 publisher 가 자기 platform 의 outcome 을 반환한다 (OCP: 새 platform 추가 = 빈 1개만 추가)")
  void dispatchReturnsCorrectPublisher() {
    PublisherRegistry registry = new PublisherRegistry(
        List.of(
            new FixedOutcomePublisher(Platform.YOUTUBE, "yt-success"),
            new FixedOutcomePublisher(Platform.TIKTOK, "tt-success")));

    PublishOutcome ytOutcome = registry.get(Platform.YOUTUBE).publish(null);
    PublishOutcome ttOutcome = registry.get(Platform.TIKTOK).publish(null);

    assertThat(ytOutcome.externalPostId()).isEqualTo("yt-success");
    assertThat(ttOutcome.externalPostId()).isEqualTo("tt-success");
  }

  /** Real test double — implements the interface, no Mockito. */
  private record FixedOutcomePublisher(Platform platform, String externalId)
      implements SocialPublisher {
    @Override
    public Platform platform() {
      return platform;
    }

    @Override
    public PublishOutcome publish(PublishContext context) {
      return PublishOutcome.success(externalId, null, null);
    }
  }
}
