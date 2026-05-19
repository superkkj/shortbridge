package com.shortbridge.platform.video.dto.response;

import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.platform.video.domain.VideoStatus;
import java.time.Instant;
import java.util.UUID;

public record VideoResponse(
    UUID id,
    String originalFileName,
    String storageKey,
    long fileSize,
    String mimeType,
    Integer durationSeconds,
    Integer width,
    Integer height,
    String videoCodec,
    VideoStatus status,
    Instant createdAt) {

  public static VideoResponse from(Video video) {
    return new VideoResponse(
        video.getId(),
        video.getOriginalFileName(),
        video.getStorageKey(),
        video.getFileSize(),
        video.getMimeType(),
        video.getDurationSeconds(),
        video.getWidth(),
        video.getHeight(),
        video.getVideoCodec(),
        video.getStatus(),
        video.getCreatedAt());
  }
}
