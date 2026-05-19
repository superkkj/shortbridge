package com.shortbridge.platform.video.controller;

import com.shortbridge.platform.post.dto.response.PostResponse;
import com.shortbridge.platform.video.dto.response.VideoResponse;
import com.shortbridge.platform.video.service.QuickPublishFacade;
import com.shortbridge.platform.video.service.VideoFacade;
import com.shortbridge.support.security.details.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoApiController {

  private final VideoFacade videoFacade;
  private final QuickPublishFacade quickPublishFacade;

  @PostMapping
  public VideoResponse upload(CurrentUser currentUser, @RequestParam("file") MultipartFile file) {
    return videoFacade.upload(currentUser.userId(), file);
  }

  @PostMapping("/quick-publish")
  public PostResponse uploadAndPublishAll(
      CurrentUser currentUser,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "title", required = false) String title) {
    return quickPublishFacade.uploadAndPublishAll(currentUser.userId(), file, title);
  }
}
