package com.shortbridge.platform.post.service;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.common.util.IdempotencyKeys;
import com.shortbridge.common.util.JsonUtils;
import com.shortbridge.platform.post.domain.Post;
import com.shortbridge.platform.post.domain.PostStatus;
import com.shortbridge.platform.post.domain.PublishMode;
import com.shortbridge.platform.post.dto.command.CreatePostCommand;
import com.shortbridge.platform.post.dto.request.UpdatePostTargetTextRequest;
import com.shortbridge.platform.post.dto.response.PostResponse;
import com.shortbridge.platform.post.dto.response.PostTargetResponse;
import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import com.shortbridge.platform.posttarget.service.PostTargetCommandService;
import com.shortbridge.platform.posttarget.service.PostTargetQueryService;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.service.SocialAccountQueryService;
import com.shortbridge.platform.video.service.VideoQueryService;
import com.shortbridge.publish.PublishGateway;
import com.shortbridge.publish.dto.EnqueueRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PostFacade {

  private final PostQueryService postQueryService;
  private final PostCommandService postCommandService;
  private final PostTargetQueryService postTargetQueryService;
  private final PostTargetCommandService postTargetCommandService;
  private final VideoQueryService videoQueryService;
  private final SocialAccountQueryService socialAccountQueryService;
  private final PublishGateway publishGateway;

  @Transactional(readOnly = true)
  public List<PostResponse> list(UUID userId) {
    return postQueryService.list(userId).stream()
        .map(p -> PostResponse.from(p, postTargetQueryService.findByPostId(userId, p.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<PostResponse> list(UUID userId, Pageable pageable) {
    return postQueryService
        .list(userId, pageable)
        .map(p -> PostResponse.from(p, postTargetQueryService.findByPostId(userId, p.getId())));
  }

  @Transactional(readOnly = true)
  public PostResponse get(UUID userId, UUID id) {
    Post post = postQueryService.get(userId, id);
    return PostResponse.from(post, postTargetQueryService.findByPostId(userId, id));
  }

  @Transactional
  public PostResponse create(CreatePostCommand command) {
    validateScheduling(command);
    videoQueryService.get(command.userId(), command.videoId());
    socialAccountQueryService.validateConnectedPlatforms(command.userId(), command.targets());

    Post post = postCommandService.create(command);
    List<PostTarget> targets =
        command.targets().stream()
            .map(platform -> createTarget(command, post, platform))
            .toList();

    if (command.publishMode() == PublishMode.NOW) {
      targets.forEach(this::enqueueImmediately);
    }

    return PostResponse.from(post, targets);
  }

  @Transactional
  public PostResponse cancel(UUID userId, UUID postId) {
    Post post = postQueryService.get(userId, postId);
    if (post.getStatus() == PostStatus.PUBLISHED) {
      throw ShortBridgeException.of(ErrorCode.POST_ALREADY_PUBLISHED, postId);
    }
    postCommandService.cancel(post);
    postTargetQueryService.findByPostId(userId, postId).forEach(this::cancelTargetIfPending);
    return PostResponse.from(post, postTargetQueryService.findByPostId(userId, postId));
  }

  @Transactional
  public PostTargetResponse updatePlatformText(
      UUID userId, UUID postTargetId, UpdatePostTargetTextRequest request) {
    PostTarget target = postTargetQueryService.get(userId, postTargetId);
    if (target.getStatus().isTerminal()) {
      throw ShortBridgeException.of(ErrorCode.POST_NOT_RETRYABLE, target.getStatus());
    }
    target.updatePlatformText(
        request.platformTitle(),
        request.platformDescription(),
        JsonUtils.joinHashtags(request.platformHashtags()));
    return PostTargetResponse.from(target);
  }

  @Transactional
  public PostTargetResponse retry(UUID userId, UUID postTargetId) {
    PostTarget target = postTargetQueryService.get(userId, postTargetId);
    if (!target.getStatus().isRetryable()) {
      throw ShortBridgeException.of(ErrorCode.POST_NOT_RETRYABLE, target.getStatus());
    }
    target.resetForRetry();
    if (postTargetCommandService.tryMarkQueuedFromReady(target.getId())) {
      publishGateway.enqueue(
          EnqueueRequest.of(target.getId(), target.getPostId(), userId, target.getPlatform()));
    }
    return PostTargetResponse.from(target);
  }

  private void validateScheduling(CreatePostCommand command) {
    if (command.publishMode() == PublishMode.SCHEDULED && command.scheduledAt() == null) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "scheduledAt is required for SCHEDULED");
    }
  }

  private PostTarget createTarget(CreatePostCommand command, Post post, Platform platform) {
    SocialAccount socialAccount = socialAccountQueryService.getConnected(command.userId(), platform);
    PostTargetStatus initial =
        command.publishMode() == PublishMode.DRAFT ? PostTargetStatus.READY : PostTargetStatus.READY;
    PostTarget target =
        PostTarget.builder()
            .userId(command.userId())
            .postId(post.getId())
            .socialAccountId(socialAccount.getId())
            .platform(platform)
            .platformTitle(command.title())
            .platformDescription(command.description())
            .platformHashtags(JsonUtils.joinHashtags(command.hashtags()))
            .privacyStatus("PRIVATE")
            .scheduledAt(command.scheduledAt())
            .initialStatus(initial)
            .idempotencyKey(IdempotencyKeys.forPostTarget(post.getId(), platform.name()))
            .build();
    return postTargetCommandService.create(target);
  }

  private void enqueueImmediately(PostTarget target) {
    target.markQueued();
    publishGateway.enqueue(
        EnqueueRequest.of(target.getId(), target.getPostId(), target.getUserId(), target.getPlatform()));
  }

  private void cancelTargetIfPending(PostTarget target) {
    if (!target.getStatus().isTerminal()) {
      target.markCanceled();
    }
  }
}
