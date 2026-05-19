package com.shortbridge.platform.post.domain;

import com.shortbridge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Post extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "video_id", nullable = false)
  private UUID videoId;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @Column(columnDefinition = "text")
  private String hashtags;

  @Enumerated(EnumType.STRING)
  @Column(name = "publish_mode", nullable = false, length = 30)
  private PublishMode publishMode;

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private PostStatus status;

  @Builder
  private Post(
      UUID userId,
      UUID videoId,
      String title,
      String description,
      String hashtags,
      PublishMode publishMode,
      Instant scheduledAt) {
    this.userId = userId;
    this.videoId = videoId;
    this.title = title;
    this.description = description;
    this.hashtags = hashtags;
    this.publishMode = publishMode == null ? PublishMode.DRAFT : publishMode;
    this.scheduledAt = scheduledAt;
    this.status =
        this.publishMode == PublishMode.NOW
            ? PostStatus.QUEUED
            : (this.publishMode == PublishMode.SCHEDULED ? PostStatus.QUEUED : PostStatus.DRAFT);
  }

  @PrePersist
  void assignId() {
    if (id == null) id = UUID.randomUUID();
  }

  public void changeStatus(PostStatus next) {
    this.status = next;
  }

  public void cancel() {
    this.status = PostStatus.CANCELED;
  }
}
