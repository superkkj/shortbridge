package com.shortbridge.platform.video.service;

import com.shortbridge.platform.video.domain.Video;
import com.shortbridge.platform.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VideoCommandService {

  private final VideoRepository videoRepository;

  public Video create(Video entity) {
    return videoRepository.save(entity);
  }

  public void delete(Video video) {
    video.markDeleted();
  }
}
