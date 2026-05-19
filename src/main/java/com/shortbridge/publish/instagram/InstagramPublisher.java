package com.shortbridge.publish.instagram;

import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.publish.dto.PublishOutcome;
import com.shortbridge.publish.publisher.PublishContext;
import com.shortbridge.publish.publisher.SocialPublisher;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InstagramPublisher implements SocialPublisher {

  @Override
  public Platform platform() {
    return Platform.INSTAGRAM;
  }

  @Override
  public PublishOutcome publish(PublishContext context) {
    log.info("instagram publish stub: postTargetId={}", context.target().getId());
    // TODO: Instagram Content Publishing
    //  - presigned URL 또는 public URL 생성
    //  - POST /{ig-user-id}/media (media_type=REELS, video_url=...)
    //  - container status polling
    //  - POST /{ig-user-id}/media_publish
    //  - Professional 계정 검증
    String stubContainerId = "ig-container-" + UUID.randomUUID();
    return PublishOutcome.success("ig-stub-" + UUID.randomUUID(), stubContainerId, null);
  }
}
