package com.shortbridge.publish.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import com.shortbridge.common.storage.StorageClient;
import com.shortbridge.common.storage.StoredObject;
import com.shortbridge.connect.connector.ConnectorProperties;
import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.publish.dto.PublishOutcome;
import com.shortbridge.publish.publisher.PublishContext;
import com.shortbridge.support.security.token.PlatformTokenCipher;
import com.shortbridge.support.security.token.TokenCipherProperties;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InstagramPublisher")
class InstagramPublisherTest {

  @Test
  @DisplayName("Platform 은 INSTAGRAM 을 반환한다 (Registry 자동 등록 키)")
  void platform_returnsInstagram() {
    InstagramPublisher publisher = new InstagramPublisher(null, null, null, null);
    assertThat(publisher.platform()).isEqualTo(Platform.INSTAGRAM);
  }

  @Test
  @DisplayName("localhost 영상 URL 은 Meta 가 가져갈 수 없으므로 capability 차단한다")
  void publish_localhostVideoUrl_returnsBlockedByCapability() {
    PlatformTokenCipher cipher =
        new PlatformTokenCipher(new TokenCipherProperties(Map.of("v1", "test-key"), "v1"));
    InstagramPublisher publisher =
        new InstagramPublisher(
            cipher,
            new FixedUrlStorageClient("http://localhost:8080/files/test.mp4"),
            new ConnectorProperties("http://localhost:8080", null, null, null),
            null);
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
    SocialAccount account =
        SocialAccount.builder()
            .userId(UUID.randomUUID())
            .platform(Platform.INSTAGRAM)
            .platformUserId("ig-user-id")
            .accessTokenEncrypted(cipher.encrypt("page-token"))
            .build();
    Video video =
        Video.builder()
            .userId(UUID.randomUUID())
            .originalFileName("test.mp4")
            .storageKey("test.mp4")
            .fileSize(1024L)
            .mimeType("video/mp4")
            .durationSeconds(10)
            .build();
    PublishContext context = new PublishContext(target, account, video, List.of());

    PublishOutcome outcome = publisher.publish(context);

    assertThat(outcome.resultType()).isEqualTo(PublishOutcome.ResultType.BLOCKED_BY_CAPABILITY);
    assertThat(outcome.externalPostId()).isNull();
    assertThat(outcome.errorMessage()).contains("public video URL");
  }

  private record FixedUrlStorageClient(String url) implements StorageClient {
    @Override
    public StoredObject store(String key, String contentType, long contentLength, InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public InputStream open(String key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete(String key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String publicUrl(String key) {
      return url;
    }

    @Override
    public String presignedGetUrl(String key, Duration ttl) {
      return url;
    }
  }
}
