package com.shortbridge.publish.scheduler;

import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.service.PostTargetCommandService;
import com.shortbridge.platform.posttarget.service.PostTargetQueryService;
import com.shortbridge.publish.PublishGateway;
import com.shortbridge.publish.dto.EnqueueRequest;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPublishEnqueuer {

  private final PostTargetQueryService postTargetQueryService;
  private final PostTargetCommandService postTargetCommandService;
  private final PublishGateway publishGateway;

  @Scheduled(fixedDelayString = "${shortbridge.publish.scheduler.fixed-delay-ms:5000}")
  @Transactional
  public void enqueueDue() {
    Instant now = Instant.now();
    List<PostTarget> due = postTargetQueryService.findDueReady(now);
    for (PostTarget target : due) {
      if (postTargetCommandService.tryMarkQueuedFromReady(target.getId())) {
        publishGateway.enqueue(
            EnqueueRequest.of(target.getId(), target.getPostId(), target.getUserId(), target.getPlatform()));
      }
    }
    List<PostTarget> retries = postTargetQueryService.findDueRetry(now);
    for (PostTarget target : retries) {
      if (postTargetCommandService.tryMarkQueuedFromRetryWait(target.getId())) {
        publishGateway.enqueue(
            EnqueueRequest.of(target.getId(), target.getPostId(), target.getUserId(), target.getPlatform()));
      }
    }
    if (!due.isEmpty() || !retries.isEmpty()) {
      log.info("scheduled enqueue: ready={} retry={}", due.size(), retries.size());
    }
  }
}
