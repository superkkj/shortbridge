package com.shortbridge.platform.video.domain;

import com.shortbridge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "videos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Video extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "storage_key", nullable = false, length = 500)
  private String storageKey;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "mime_type", length = 100)
  private String mimeType;

  @Column(name = "duration_sec")
  private Integer durationSeconds;

  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  @Column(name = "video_codec", length = 100)
  private String videoCodec;

  @Column(name = "audio_codec", length = 100)
  private String audioCodec;

  @Column(name = "aspect_ratio", length = 20)
  private String aspectRatio;

  @Column(length = 255)
  private String checksum;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private VideoStatus status;

  @Builder
  private Video(
      UUID userId,
      String originalFileName,
      String storageKey,
      long fileSize,
      String mimeType,
      Integer durationSeconds,
      Integer width,
      Integer height,
      String videoCodec,
      String audioCodec,
      String aspectRatio,
      String checksum) {
    this.userId = userId;
    this.originalFileName = originalFileName;
    this.storageKey = storageKey;
    this.fileSize = fileSize;
    this.mimeType = mimeType;
    this.durationSeconds = durationSeconds;
    this.width = width;
    this.height = height;
    this.videoCodec = videoCodec;
    this.audioCodec = audioCodec;
    this.aspectRatio = aspectRatio;
    this.checksum = checksum;
    this.status = VideoStatus.UPLOADED;
  }

  @PrePersist
  void assignId() {
    if (id == null) id = UUID.randomUUID();
  }

  public void markReady() {
    this.status = VideoStatus.READY;
  }

  public void markDeleted() {
    this.status = VideoStatus.DELETED;
  }
}
