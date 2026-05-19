package com.shortbridge.platform.post.service;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.platform.post.domain.Post;
import com.shortbridge.platform.post.repository.PostRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostQueryService {

  private final PostRepository postRepository;

  public Post get(UUID userId, UUID id) {
    return postRepository
        .findOne(id, userId)
        .orElseThrow(() -> ShortBridgeException.of(ErrorCode.POST_NOT_FOUND, id));
  }

  public List<Post> list(UUID userId) {
    return postRepository.findByUserIdOrderByCreatedAtDesc(userId);
  }
}
