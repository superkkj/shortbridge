package com.shortbridge.platform.video.service;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.common.storage.StorageClient;
import com.shortbridge.common.storage.StoredObject;
import com.shortbridge.platform.video.VideoProperties;
import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.platform.video.dto.response.VideoResponse;
import com.shortbridge.support.ffmpeg.FfprobeRunner;
import com.shortbridge.support.ffmpeg.VideoMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(VideoProperties.class)
public class VideoFacade {

  private final VideoCommandService commandService;
  private final VideoQueryService queryService;
  private final StorageClient storageClient;
  private final FfprobeRunner ffprobeRunner;
  private final VideoProperties videoProperties;

  @Transactional(readOnly = true)
  public List<VideoResponse> list(UUID userId) {
    return queryService.list(userId).stream().map(VideoResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public VideoResponse get(UUID userId, UUID id) {
    return VideoResponse.from(queryService.get(userId, id));
  }

  @Transactional
  public VideoResponse upload(UUID userId, MultipartFile file) {
    validatePresence(file);
    validateSize(file.getSize());
    validateMimeType(file.getContentType());

    Path tempFile = writeTempFile(file);
    try {
      VideoMetadata metadata = ffprobeRunner.probe(tempFile);
      validateMetadata(metadata);

      String storageKey = buildStorageKey(file.getOriginalFilename());
      try (InputStream is = Files.newInputStream(tempFile)) {
        StoredObject stored = storageClient.store(storageKey, file.getContentType(), file.getSize(), is);
        Video video =
            commandService.create(
                Video.builder()
                    .userId(userId)
                    .originalFileName(safeFilename(file.getOriginalFilename()))
                    .storageKey(stored.key())
                    .fileSize(stored.size())
                    .mimeType(file.getContentType())
                    .durationSeconds(metadata.durationSeconds())
                    .width(metadata.width())
                    .height(metadata.height())
                    .videoCodec(metadata.videoCodec())
                    .audioCodec(metadata.audioCodec())
                    .aspectRatio(aspectRatio(metadata.width(), metadata.height()))
                    .build());
        video.markReady();
        return VideoResponse.from(video);
      } catch (IOException e) {
        throw ShortBridgeException.of(ErrorCode.STORAGE_ERROR, e);
      }
    } finally {
      safeDelete(tempFile);
    }
  }

  private void validatePresence(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw ShortBridgeException.of(ErrorCode.INVALID_REQUEST, "file");
    }
  }

  private void validateSize(long size) {
    if (size > videoProperties.maxFileSizeBytes()) {
      throw ShortBridgeException.of(ErrorCode.VIDEO_SIZE_EXCEEDED, size);
    }
  }

  private void validateMimeType(String mime) {
    if (mime == null || videoProperties.allowedMimeTypes().stream().noneMatch(m -> m.equalsIgnoreCase(mime))) {
      throw ShortBridgeException.of(ErrorCode.VIDEO_INVALID_FORMAT, mime);
    }
  }

  private void validateMetadata(VideoMetadata metadata) {
    if (metadata.durationSeconds() > videoProperties.maxDurationSeconds()) {
      throw ShortBridgeException.of(ErrorCode.VIDEO_DURATION_EXCEEDED, metadata.durationSeconds());
    }
    if (metadata.videoCodec() != null
        && videoProperties.allowedVideoCodecs().stream().noneMatch(c -> c.equalsIgnoreCase(metadata.videoCodec()))) {
      throw ShortBridgeException.of(ErrorCode.VIDEO_INVALID_FORMAT, "video=" + metadata.videoCodec());
    }
    if (metadata.audioCodec() != null
        && videoProperties.allowedAudioCodecs().stream().noneMatch(c -> c.equalsIgnoreCase(metadata.audioCodec()))) {
      throw ShortBridgeException.of(ErrorCode.VIDEO_INVALID_FORMAT, "audio=" + metadata.audioCodec());
    }
  }

  private Path writeTempFile(MultipartFile file) {
    try {
      Path temp = Files.createTempFile("shortbridge-upload-", ".tmp");
      file.transferTo(temp.toFile());
      return temp;
    } catch (IOException e) {
      throw ShortBridgeException.of(ErrorCode.STORAGE_ERROR, e);
    }
  }

  private void safeDelete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("temp file delete failed: {}", path, e);
    }
  }

  private static String buildStorageKey(String originalFilename) {
    LocalDate today = LocalDate.now();
    String suffix = extension(originalFilename);
    return "videos/%d/%02d/%02d/%s%s"
        .formatted(today.getYear(), today.getMonthValue(), today.getDayOfMonth(), UUID.randomUUID(), suffix);
  }

  private static String extension(String filename) {
    if (filename == null) return "";
    int dot = filename.lastIndexOf('.');
    return dot < 0 ? "" : filename.substring(dot).toLowerCase();
  }

  private static String safeFilename(String filename) {
    if (filename == null) return "video";
    int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
    return slash < 0 ? filename : filename.substring(slash + 1);
  }

  private static String aspectRatio(Integer width, Integer height) {
    if (width == null || height == null || height == 0) return null;
    int gcd = gcd(width, height);
    return (width / gcd) + ":" + (height / gcd);
  }

  private static int gcd(int a, int b) {
    return b == 0 ? a : gcd(b, a % b);
  }
}
