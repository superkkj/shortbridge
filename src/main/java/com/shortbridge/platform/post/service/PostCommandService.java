package com.shortbridge.platform.post.service;

import com.shortbridge.platform.post.domain.Post;
import com.shortbridge.platform.post.domain.PostStatus;
import com.shortbridge.platform.post.dto.command.CreatePostCommand;
import com.shortbridge.platform.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostCommandService {

  private final PostRepository postRepository;

  public Post create(CreatePostCommand command) {
    return postRepository.save(command.toEntity());
  }

  public void updateStatus(Post post, PostStatus status) {
    post.changeStatus(status);
  }

  public void cancel(Post post) {
    post.cancel();
  }
}
