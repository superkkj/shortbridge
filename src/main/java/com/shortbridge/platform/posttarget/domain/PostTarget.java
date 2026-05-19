package com.shortbridge.platform.posttarget.domain;

import com.shortbridge.common.domain.BaseEntity;
import com.shortbridge.platform.socialaccount.domain.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "post_targets",
    uniqueConstraints = {
      @UniqueConstraint(name = "ux_post_targets_post_platform", columnNames = {"post_id", "platform"}),
      @UniqueConstraint(name = "ux_post_targets_idempotency_key", columnNames = "idempotency_key")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PostTarget extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "post_id", nullable = false)
  private UUID postId;

  @Column(name = "social_account_id", nullable = false)
  private UUID socialAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Platform platform;

  @Column(name = "platform_title", length = 255)
  private String platformTitle;

  @Column(name = "platform_description", columnDefinition = "text")
  private String platformDescription;

  @Column(name = "platform_hashtags", columnDefinition = "text")
  private String platformHashtags;

  @Column(name = "privacy_status", length = 30)
  private String privacyStatus;

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private PostTargetStatus status;

  @Column(name = "idempotency_key", nullable = false, length = 120)
  private String idempotencyKey;

  @Column(name = "external_post_id", length = 255)
  private String externalPostId;

  @Column(name = "external_publish_id", length = 255)
  private String externalPublishId;

  @Column(name = "external_media_id", length = 255)
  private String externalMediaId;

  @Column(name = "error_code", length = 255)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "raw_response_json", columnDefinition = "jsonb")
  private String rawResponseJson;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  @Column(name = "queued_at")
  private Instant queuedAt;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "locked_by", length = 100)
  private String lockedBy;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Builder
  private PostTarget(
      UUID userId,
      UUID postId,
      UUID socialAccountId,
      Platform platform,
      String platformTitle,
      String platformDescription,
      String platformHashtags,
      String privacyStatus,
      Instant scheduledAt,
      PostTargetStatus initialStatus,
      String idempotencyKey) {
    this.userId = userId;
    this.postId = postId;
    this.socialAccountId = socialAccountId;
    this.platform = platform;
    this.platformTitle = platformTitle;
    this.platformDescription = platformDescription;
    this.platformHashtags = platformHashtags;
    this.privacyStatus = privacyStatus;
    this.scheduledAt = scheduledAt;
    this.status = initialStatus == null ? PostTargetStatus.READY : initialStatus;
    this.idempotencyKey = idempotencyKey;
    this.retryCount = 0;
  }

  @PrePersist
  void assignId() {
    if (id == null) id = UUID.randomUUID();
  }

  public void markQueued() {
    this.status = PostTargetStatus.QUEUED;
    this.queuedAt = Instant.now();
  }

  public void markUploading() {
    this.status = PostTargetStatus.UPLOADING;
    this.lastAttemptAt = Instant.now();
  }

  public void markProcessing(String externalPublishId) {
    this.status = PostTargetStatus.PROCESSING;
    if (externalPublishId != null) this.externalPublishId = externalPublishId;
  }

  public void markPublished(String externalPostId) {
    this.status = PostTargetStatus.PUBLISHED;
    this.externalPostId = externalPostId;
    this.publishedAt = Instant.now();
    this.errorCode = null;
    this.errorMessage = null;
  }

  public void markFailedTemporary(String code, String message, String rawJson) {
    this.status = PostTargetStatus.FAILED_TEMPORARY;
    this.errorCode = code;
    this.errorMessage = message;
    this.rawResponseJson = rawJson;
    this.retryCount += 1;
  }

  public void markFailedPermanent(String code, String message, String rawJson) {
    this.status = PostTargetStatus.FAILED_PERMANENT;
    this.errorCode = code;
    this.errorMessage = message;
    this.rawResponseJson = rawJson;
  }

  public void markRetryWait(Instant nextRetryAt) {
    this.status = PostTargetStatus.RETRY_WAIT;
    this.nextRetryAt = nextRetryAt;
  }

  public void markReconnectRequired(String message) {
    this.status = PostTargetStatus.RECONNECT_REQUIRED;
    this.errorMessage = message;
  }

  public void markBlockedByQuota(Instant nextRetryAt) {
    this.status = PostTargetStatus.BLOCKED_BY_QUOTA;
    this.nextRetryAt = nextRetryAt;
  }

  public void markBlockedByCapability(String message) {
    this.status = PostTargetStatus.BLOCKED_BY_CAPABILITY;
    this.errorMessage = message;
  }

  public void markPrivateLimited(String message) {
    this.status = PostTargetStatus.PRIVATE_LIMITED;
    this.errorMessage = message;
  }

  public void markCanceled() {
    this.status = PostTargetStatus.CANCELED;
  }

  public void resetForRetry() {
    if (!status.isRetryable()) {
      throw new IllegalStateException("not retryable: " + status);
    }
    this.status = PostTargetStatus.READY;
    this.lockedAt = null;
    this.lockedBy = null;
    this.nextRetryAt = null;
  }

  public void updatePlatformText(String title, String description, String hashtags) {
    if (title != null) this.platformTitle = title;
    if (description != null) this.platformDescription = description;
    if (hashtags != null) this.platformHashtags = hashtags;
  }
}
