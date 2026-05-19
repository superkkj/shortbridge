package com.shortbridge.platform.video.service;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.platform.video.repository.VideoRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VideoQueryService {

  private final VideoRepository videoRepository;

  public Video get(UUID userId, UUID id) {
    return videoRepository
        .findOne(id, userId)
        .orElseThrow(() -> ShortBridgeException.of(ErrorCode.VIDEO_NOT_FOUND, id));
  }

  public List<Video> list(UUID userId) {
    return videoRepository.findByUserIdOrderByCreatedAtDesc(userId);
  }
}
