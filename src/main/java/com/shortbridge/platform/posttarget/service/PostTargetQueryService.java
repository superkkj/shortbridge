package com.shortbridge.platform.posttarget.service;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.repository.PostTargetRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostTargetQueryService {

  private final PostTargetRepository postTargetRepository;

  public PostTarget get(UUID userId, UUID id) {
    return postTargetRepository
        .findOne(id, userId)
        .orElseThrow(() -> ShortBridgeException.of(ErrorCode.POST_TARGET_NOT_FOUND, id));
  }

  public PostTarget getInternal(UUID id) {
    return postTargetRepository
        .findById(id)
        .orElseThrow(() -> ShortBridgeException.of(ErrorCode.POST_TARGET_NOT_FOUND, id));
  }

  public List<PostTarget> findByPostId(UUID userId, UUID postId) {
    return postTargetRepository.findByPostIdAndUserIdOrderByPlatformAsc(postId, userId);
  }

  public List<PostTarget> findDueReady(Instant now) {
    return postTargetRepository.findDueReadyTargets(now);
  }

  public List<PostTarget> findDueRetry(Instant now) {
    return postTargetRepository.findDueRetryTargets(now);
  }
}
