package com.shortbridge.platform.posttarget.service;

import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import com.shortbridge.platform.posttarget.repository.PostTargetRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostTargetCommandService {

  private final PostTargetRepository postTargetRepository;

  public PostTarget create(PostTarget target) {
    return postTargetRepository.save(target);
  }

  @Transactional
  public boolean tryLockForPublish(UUID postTargetId, String workerId) {
    int updated =
        postTargetRepository.lockForPublish(
            postTargetId,
            workerId,
            Instant.now(),
            List.of(PostTargetStatus.READY, PostTargetStatus.QUEUED, PostTargetStatus.RETRY_WAIT));
    return updated > 0;
  }

  @Transactional
  public boolean tryMarkQueuedFromReady(UUID postTargetId) {
    int updated = postTargetRepository.markQueuedConditional(postTargetId, Instant.now());
    return updated > 0;
  }
}
