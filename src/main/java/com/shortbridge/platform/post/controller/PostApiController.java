package com.shortbridge.platform.post.controller;

import com.shortbridge.platform.post.dto.request.CreatePostRequest;
import com.shortbridge.platform.post.dto.request.UpdatePostTargetTextRequest;
import com.shortbridge.platform.post.dto.response.PostResponse;
import com.shortbridge.platform.post.dto.response.PostTargetResponse;
import com.shortbridge.platform.post.service.PostFacade;
import com.shortbridge.support.security.details.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostApiController {

  private final PostFacade postFacade;

  @PostMapping
  public PostResponse create(CurrentUser currentUser, @Valid @RequestBody CreatePostRequest request) {
    return postFacade.create(request.toCommand(currentUser.userId()));
  }

  @DeleteMapping("/{id}")
  public PostResponse cancel(CurrentUser currentUser, @PathVariable UUID id) {
    return postFacade.cancel(currentUser.userId(), id);
  }

  @PatchMapping("/targets/{postTargetId}")
  public PostTargetResponse updatePlatformText(
      CurrentUser currentUser,
      @PathVariable UUID postTargetId,
      @Valid @RequestBody UpdatePostTargetTextRequest request) {
    return postFacade.updatePlatformText(currentUser.userId(), postTargetId, request);
  }

  @PostMapping("/targets/{postTargetId}/retry")
  public PostTargetResponse retry(CurrentUser currentUser, @PathVariable UUID postTargetId) {
    return postFacade.retry(currentUser.userId(), postTargetId);
  }
}
