package com.shortbridge.platform.publishjob.service;

import com.shortbridge.common.util.IdempotencyKeys;
import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.publishjob.domain.PublishJob;
import com.shortbridge.platform.publishjob.repository.PublishJobRepository;
import com.shortbridge.platform.socialaccount.domain.Platform;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublishJobCommandService {

  private final PublishJobRepository publishJobRepository;

  public PublishJob enqueue(PostTarget target) {
    long previousAttempts = publishJobRepository.countByPostTargetId(target.getId());
    return publishJobRepository.save(
        PublishJob.builder()
            .postTargetId(target.getId())
            .platform(target.getPlatform())
            .queueName(target.getPlatform().queueName())
            .idempotencyKey(IdempotencyKeys.forPostTarget(target.getPostId(), target.getPlatform().name()))
            .attempt((int) previousAttempts + 1)
            .build());
  }

  public PublishJob enqueueStub(UUID postTargetId, UUID postId, Platform platform) {
    long previousAttempts = publishJobRepository.countByPostTargetId(postTargetId);
    return publishJobRepository.save(
        PublishJob.builder()
            .postTargetId(postTargetId)
            .platform(platform)
            .queueName(platform.queueName())
            .idempotencyKey(IdempotencyKeys.forPostTarget(postId, platform.name()))
            .attempt((int) previousAttempts + 1)
            .build());
  }
}
