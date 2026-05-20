package com.shortbridge.platform.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.common.util.IdempotencyKeys;
import com.shortbridge.platform.post.domain.Post;
import com.shortbridge.platform.post.domain.PostStatus;
import com.shortbridge.platform.post.domain.PublishMode;
import com.shortbridge.platform.post.dto.command.CreatePostCommand;
import com.shortbridge.platform.post.dto.response.PostResponse;
import com.shortbridge.platform.post.dto.response.PostTargetResponse;
import com.shortbridge.platform.post.repository.PostRepository;
import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import com.shortbridge.platform.posttarget.repository.PostTargetRepository;
import com.shortbridge.platform.publishjob.repository.PublishJobRepository;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.repository.SocialAccountRepository;
import com.shortbridge.platform.user.domain.User;
import com.shortbridge.platform.user.repository.UserRepository;
import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.platform.video.repository.VideoRepository;
import com.shortbridge.publish.dto.PublishMessage;
import com.shortbridge.support.AbstractIntegrationTest;
import com.shortbridge.support.rabbitmq.QueueNames;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("PostFacade 통합 (실제 Postgres + 실제 RabbitMQ)")
class PostFacadeIntegrationTest extends AbstractIntegrationTest {

  @Autowired PostFacade postFacade;
  @Autowired UserRepository userRepository;
  @Autowired VideoRepository videoRepository;
  @Autowired SocialAccountRepository socialAccountRepository;
  @Autowired PostRepository postRepository;
  @Autowired PostTargetRepository postTargetRepository;
  @Autowired PublishJobRepository publishJobRepository;
  @Autowired RabbitTemplate rabbitTemplate;

  private User user;
  private UUID videoId;

  @BeforeEach
  void seed() {
    drainQueue(QueueNames.YOUTUBE);
    drainQueue(QueueNames.INSTAGRAM);
    drainQueue(QueueNames.TIKTOK);

    publishJobRepository.deleteAll();
    postTargetRepository.deleteAll();
    postRepository.deleteAll();
    videoRepository.deleteAll();
    socialAccountRepository.deleteAll();
    userRepository.deleteAll();

    user = userRepository.save(
        User.builder().email("integ@example.com").displayName("Integ").build());
    Video video = videoRepository.save(
        Video.builder()
            .userId(user.getId())
            .originalFileName("test.mp4")
            .storageKey("local/test.mp4")
            .fileSize(1_000_000L)
            .mimeType("video/mp4")
            .durationSeconds(15)
            .build());
    videoId = video.getId();
    socialAccountRepository.save(
        SocialAccount.builder()
            .userId(user.getId())
            .platform(Platform.YOUTUBE)
            .platformUserId("yt-1")
            .displayName("My YT")
            .build());
  }

  private void drainQueue(String queue) {
    while (rabbitTemplate.receive(queue) != null) {
      // basicGet, no timeout — null when queue empty
    }
  }

  private PublishMessage waitForMessage(String queue) {
    for (int i = 0; i < 30; i++) {
      Object msg = rabbitTemplate.receiveAndConvert(queue);
      if (msg instanceof PublishMessage pm) {
        return pm;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("interrupted while waiting for message");
      }
    }
    throw new AssertionError("no PublishMessage arrived on queue " + queue);
  }

  @Test
  @DisplayName("create(NOW) — Post/PostTarget 저장 + publish_jobs row 생성 + queue 에 실제 메시지 적재")
  void create_now_persistsAndEnqueuesRealMessage() {
    CreatePostCommand command = new CreatePostCommand(
        user.getId(),
        videoId,
        "Hello",
        "World",
        List.of("a", "b"),
        PublishMode.NOW,
        null,
        List.of(Platform.YOUTUBE));

    PostResponse response = postFacade.create(command);

    assertThat(response.title()).isEqualTo("Hello");
    assertThat(response.targets()).hasSize(1);

    Post savedPost = postRepository.findOne(response.id(), user.getId()).orElseThrow();
    assertThat(savedPost.getStatus()).isEqualTo(PostStatus.QUEUED);

    List<PostTarget> savedTargets =
        postTargetRepository.findByPostIdAndUserIdOrderByPlatformAsc(response.id(), user.getId());
    assertThat(savedTargets).hasSize(1);
    assertThat(savedTargets.get(0).getStatus()).isEqualTo(PostTargetStatus.QUEUED);
    assertThat(savedTargets.get(0).getIdempotencyKey())
        .isEqualTo(IdempotencyKeys.forPostTarget(response.id(), Platform.YOUTUBE.name()));

    assertThat(publishJobRepository.countByPostTargetId(savedTargets.get(0).getId())).isEqualTo(1L);

    PublishMessage message = waitForMessage(QueueNames.YOUTUBE);
    assertThat(message.postTargetId()).isEqualTo(savedTargets.get(0).getId());
    assertThat(message.postId()).isEqualTo(response.id());
    assertThat(message.platform()).isEqualTo(Platform.YOUTUBE);
    assertThat(message.attempt()).isEqualTo(1);
  }

  @Test
  @DisplayName("create(DRAFT) — Post 저장 + status=DRAFT + publish_jobs 미생성 + queue 비어있음")
  void create_draft_doesNotPublish() {
    CreatePostCommand command = new CreatePostCommand(
        user.getId(),
        videoId,
        "Draft",
        "Body",
        List.of(),
        PublishMode.DRAFT,
        null,
        List.of(Platform.YOUTUBE));

    PostResponse response = postFacade.create(command);

    Post savedPost = postRepository.findOne(response.id(), user.getId()).orElseThrow();
    assertThat(savedPost.getStatus()).isEqualTo(PostStatus.DRAFT);

    List<PostTarget> targets =
        postTargetRepository.findByPostIdAndUserIdOrderByPlatformAsc(response.id(), user.getId());
    assertThat(targets.get(0).getStatus()).isEqualTo(PostTargetStatus.READY);

    assertThat(publishJobRepository.count()).isZero();
    assertThat(rabbitTemplate.receive(QueueNames.YOUTUBE)).isNull();
  }

  @Test
  @DisplayName("create(SCHEDULED) — scheduledAt null 이면 INVALID_REQUEST 예외")
  void create_scheduledWithoutAt_throws() {
    CreatePostCommand command = new CreatePostCommand(
        user.getId(),
        videoId,
        "Sched",
        "",
        List.of(),
        PublishMode.SCHEDULED,
        null,
        List.of(Platform.YOUTUBE));

    assertThatThrownBy(() -> postFacade.create(command))
        .isInstanceOf(ShortBridgeException.class)
        .extracting(e -> ((ShortBridgeException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("create — 연결되지 않은 플랫폼이 targets 에 있으면 SOCIAL_ACCOUNT_NOT_CONNECTED")
  void create_disconnectedPlatform_throws() {
    CreatePostCommand command = new CreatePostCommand(
        user.getId(),
        videoId,
        "X",
        "",
        List.of(),
        PublishMode.NOW,
        null,
        List.of(Platform.TIKTOK));

    assertThatThrownBy(() -> postFacade.create(command))
        .isInstanceOf(ShortBridgeException.class)
        .extracting(e -> ((ShortBridgeException) e).getErrorCode())
        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_NOT_CONNECTED);
  }

  @Test
  @DisplayName("cancel(DRAFT) — Post + 미종료 Target 모두 CANCELED")
  void cancel_draft_setsAllCanceled() {
    PostResponse created = postFacade.create(new CreatePostCommand(
        user.getId(), videoId, "D", "", List.of(),
        PublishMode.DRAFT, null, List.of(Platform.YOUTUBE)));

    PostResponse canceled = postFacade.cancel(user.getId(), created.id());

    assertThat(canceled.status()).isEqualTo(PostStatus.CANCELED);
    Post post = postRepository.findOne(created.id(), user.getId()).orElseThrow();
    assertThat(post.getStatus()).isEqualTo(PostStatus.CANCELED);

    List<PostTarget> targets =
        postTargetRepository.findByPostIdAndUserIdOrderByPlatformAsc(created.id(), user.getId());
    assertThat(targets).allMatch(t -> t.getStatus() == PostTargetStatus.CANCELED);
  }

  @Test
  @DisplayName("cancel(PUBLISHED) — POST_ALREADY_PUBLISHED 예외")
  void cancel_published_throws() {
    PostResponse created = postFacade.create(new CreatePostCommand(
        user.getId(), videoId, "P", "", List.of(),
        PublishMode.DRAFT, null, List.of(Platform.YOUTUBE)));

    Post post = postRepository.findOne(created.id(), user.getId()).orElseThrow();
    post.changeStatus(PostStatus.PUBLISHED);
    postRepository.save(post);

    assertThatThrownBy(() -> postFacade.cancel(user.getId(), created.id()))
        .isInstanceOf(ShortBridgeException.class)
        .extracting(e -> ((ShortBridgeException) e).getErrorCode())
        .isEqualTo(ErrorCode.POST_ALREADY_PUBLISHED);
  }

  @Test
  @DisplayName("retry(FAILED_TEMPORARY) — READY 재설정 + QUEUED 전환 + publish_jobs row + queue 메시지")
  void retry_retryable_resetsAndEnqueuesRealMessage() {
    PostResponse created = postFacade.create(new CreatePostCommand(
        user.getId(), videoId, "R", "", List.of(),
        PublishMode.DRAFT, null, List.of(Platform.YOUTUBE)));
    PostTarget target = postTargetRepository
        .findByPostIdAndUserIdOrderByPlatformAsc(created.id(), user.getId())
        .get(0);
    target.markFailedTemporary("CODE", "msg", null);
    postTargetRepository.save(target);

    PostTargetResponse retried = postFacade.retry(user.getId(), target.getId());

    assertThat(retried.id()).isEqualTo(target.getId());
    PostTarget refreshed = postTargetRepository.findById(target.getId()).orElseThrow();
    assertThat(refreshed.getStatus()).isEqualTo(PostTargetStatus.QUEUED);

    assertThat(publishJobRepository.countByPostTargetId(target.getId())).isEqualTo(1L);

    PublishMessage message = waitForMessage(QueueNames.YOUTUBE);
    assertThat(message.postTargetId()).isEqualTo(target.getId());
  }

  @Test
  @DisplayName("retry(PUBLISHED) — POST_NOT_RETRYABLE 예외")
  void retry_terminalStatus_throws() {
    PostResponse created = postFacade.create(new CreatePostCommand(
        user.getId(), videoId, "T", "", List.of(),
        PublishMode.DRAFT, null, List.of(Platform.YOUTUBE)));
    PostTarget target = postTargetRepository
        .findByPostIdAndUserIdOrderByPlatformAsc(created.id(), user.getId())
        .get(0);
    target.markPublished("ext-1");
    postTargetRepository.save(target);

    UUID targetId = target.getId();
    assertThatThrownBy(() -> postFacade.retry(user.getId(), targetId))
        .isInstanceOf(ShortBridgeException.class)
        .extracting(e -> ((ShortBridgeException) e).getErrorCode())
        .isEqualTo(ErrorCode.POST_NOT_RETRYABLE);
  }
}
