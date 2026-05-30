package com.shortbridge.publish.worker;

import com.shortbridge.common.util.JsonUtils;
import com.shortbridge.platform.post.domain.Post;
import com.shortbridge.platform.post.domain.PostStatus;
import com.shortbridge.platform.post.repository.PostRepository;
import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import com.shortbridge.platform.posttarget.service.PostTargetCommandService;
import com.shortbridge.platform.posttarget.service.PostTargetQueryService;
import com.shortbridge.platform.publishjob.domain.PublishJob;
import com.shortbridge.platform.publishjob.repository.PublishJobRepository;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.service.SocialAccountQueryService;
import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.platform.video.service.VideoQueryService;
import com.shortbridge.publish.dto.PublishMessage;
import com.shortbridge.publish.dto.PublishOutcome;
import com.shortbridge.publish.publisher.PublishContext;
import com.shortbridge.publish.publisher.PublisherRegistry;
import com.shortbridge.publish.publisher.SocialPublisher;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PublishProcessor {

  private final PostTargetQueryService postTargetQueryService;
  private final PostTargetCommandService postTargetCommandService;
  private final SocialAccountQueryService socialAccountQueryService;
  private final VideoQueryService videoQueryService;
  private final PostRepository postRepository;
  private final PublishJobRepository publishJobRepository;
  private final PublisherRegistry publisherRegistry;

  @Transactional
  public void process(PublishMessage message) {
    String workerId = resolveWorkerId();
    boolean locked = postTargetCommandService.tryLockForPublish(message.postTargetId(), workerId);
    if (!locked) {
      log.info("skip already-locked target: id={}", message.postTargetId());
      return;
    }

    PostTarget target = postTargetQueryService.getInternal(message.postTargetId());
    if (target.getStatus().isTerminal()) {
      log.info("skip terminal target: id={} status={}", target.getId(), target.getStatus());
      return;
    }

    PublishJob job = findOrEnsureJob(message);
    job.markProcessing(workerId);

    try {
      target.markUploading();
      SocialAccount account = socialAccountQueryService.get(target.getUserId(), target.getSocialAccountId());
      Video video = videoQueryService.get(target.getUserId(), getVideoId(target.getPostId()));
      SocialPublisher publisher = publisherRegistry.get(target.getPlatform());
      PublishOutcome outcome =
          publisher.publish(
              new PublishContext(target, account, video, JsonUtils.splitHashtags(target.getPlatformHashtags())));
      log.info(
          "publish processor outcome: target={} platform={} result={}",
          target.getId(),
          target.getPlatform(),
          outcome.resultType());
      applyOutcome(target, job, account, outcome);
    } catch (RuntimeException e) {
      log.error("publish worker failed: target={}", target.getId(), e);
      target.markFailedTemporary("WORKER_ERROR", e.getMessage(), null);
      Instant retryAt = Instant.now().plusSeconds(60);
      target.markRetryWait(retryAt);
      job.markRetryWait("WORKER_ERROR", e.getMessage(), retryAt);
    }
    updatePostStatusAfterTarget(target);
  }

  private void applyOutcome(PostTarget target, PublishJob job, SocialAccount account, PublishOutcome outcome) {
    switch (outcome.resultType()) {
      case SUCCESS -> {
        target.markPublished(outcome.externalPostId());
        job.markSucceeded();
      }
      case FAILED_TEMPORARY -> {
        target.markFailedTemporary(outcome.errorCode(), outcome.errorMessage(), outcome.rawResponseJson());
        Instant retryAt = outcome.nextRetryAt() != null ? outcome.nextRetryAt() : Instant.now().plusSeconds(60);
        target.markRetryWait(retryAt);
        job.markRetryWait(outcome.errorCode(), outcome.errorMessage(), retryAt);
      }
      case FAILED_PERMANENT -> {
        target.markFailedPermanent(outcome.errorCode(), outcome.errorMessage(), outcome.rawResponseJson());
        job.markFailed(outcome.errorCode(), outcome.errorMessage());
      }
      case RECONNECT_REQUIRED -> {
        target.markReconnectRequired(outcome.errorMessage());
        account.markReconnectRequired();
        job.markFailed(outcome.errorCode(), outcome.errorMessage());
      }
      case BLOCKED_BY_QUOTA -> {
        Instant retryAt = outcome.nextRetryAt() != null ? outcome.nextRetryAt() : Instant.now().plusSeconds(900);
        target.markBlockedByQuota(retryAt);
        job.markRetryWait(outcome.errorCode(), outcome.errorMessage(), retryAt);
      }
      case BLOCKED_BY_CAPABILITY -> {
        target.markBlockedByCapability(outcome.errorMessage());
        account.markCapabilityBlocked();
        job.markFailed(outcome.errorCode(), outcome.errorMessage());
      }
      case PRIVATE_LIMITED -> {
        target.markPrivateLimited(outcome.errorMessage());
        job.markSucceeded();
      }
    }
  }

  private PublishJob findOrEnsureJob(PublishMessage message) {
    return publishJobRepository.findByPostTargetIdOrderByAttemptAsc(message.postTargetId()).stream()
        .filter(j -> j.getAttempt() == message.attempt())
        .findFirst()
        .orElseGet(
            () ->
                publishJobRepository.save(
                    PublishJob.builder()
                        .postTargetId(message.postTargetId())
                        .platform(message.platform())
                        .queueName(message.platform().queueName())
                        .idempotencyKey(message.idempotencyKey())
                        .attempt(message.attempt())
                        .build()));
  }

  private UUID getVideoId(UUID postId) {
    Post post = postRepository.findById(postId).orElseThrow();
    return post.getVideoId();
  }

  private void updatePostStatusAfterTarget(PostTarget target) {
    Post post = postRepository.findById(target.getPostId()).orElseThrow();
    List<PostTargetStatus> statuses =
        postTargetQueryService.findByPostId(post.getUserId(), post.getId()).stream()
            .map(PostTarget::getStatus)
            .toList();
    PostStatus.recomputeFrom(statuses).ifPresent(post::changeStatus);
  }

  private static String resolveWorkerId() {
    try {
      return InetAddress.getLocalHost().getHostName() + "/" + Thread.currentThread().getName();
    } catch (Exception e) {
      return "worker/" + Thread.currentThread().getName();
    }
  }
}
