package com.shortbridge.platform.publishjob.domain;

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
    name = "publish_jobs",
    uniqueConstraints =
        @UniqueConstraint(name = "ux_publish_jobs_target_attempt", columnNames = {"post_target_id", "attempt"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PublishJob extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "post_target_id", nullable = false)
  private UUID postTargetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Platform platform;

  @Column(name = "queue_name", nullable = false, length = 100)
  private String queueName;

  @Column(name = "idempotency_key", nullable = false, length = 120)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private PublishJobStatus status;

  @Column(nullable = false)
  private int attempt;

  @Column(name = "error_code", length = 255)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "locked_by", length = 100)
  private String lockedBy;

  @Builder
  private PublishJob(
      UUID postTargetId, Platform platform, String queueName, String idempotencyKey, int attempt) {
    this.postTargetId = postTargetId;
    this.platform = platform;
    this.queueName = queueName;
    this.idempotencyKey = idempotencyKey;
    this.attempt = attempt;
    this.status = PublishJobStatus.QUEUED;
  }

  @PrePersist
  void assignId() {
    if (id == null) id = UUID.randomUUID();
  }

  public void markProcessing(String workerId) {
    this.status = PublishJobStatus.PROCESSING;
    this.startedAt = Instant.now();
    this.lockedAt = Instant.now();
    this.lockedBy = workerId;
  }

  public void markSucceeded() {
    this.status = PublishJobStatus.SUCCEEDED;
    this.finishedAt = Instant.now();
  }

  public void markRetryWait(String code, String message, Instant nextRetryAt) {
    this.status = PublishJobStatus.RETRY_WAIT;
    this.errorCode = code;
    this.errorMessage = message;
    this.nextRetryAt = nextRetryAt;
    this.finishedAt = Instant.now();
  }

  public void markFailed(String code, String message) {
    this.status = PublishJobStatus.FAILED;
    this.errorCode = code;
    this.errorMessage = message;
    this.finishedAt = Instant.now();
  }

  public void markDeadLetter(String code, String message) {
    this.status = PublishJobStatus.DEAD_LETTER;
    this.errorCode = code;
    this.errorMessage = message;
    this.finishedAt = Instant.now();
  }
}
