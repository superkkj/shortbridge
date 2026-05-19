package com.shortbridge.platform.video.controller;

import com.shortbridge.platform.video.service.VideoFacade;
import com.shortbridge.support.security.details.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/videos")
@RequiredArgsConstructor
public class VideoViewController {

  private final VideoFacade videoFacade;

  @GetMapping
  public String list(CurrentUser currentUser, Model model) {
    model.addAttribute("videos", videoFacade.list(currentUser.userId()));
    return "videos/list";
  }

  @GetMapping("/new")
  public String uploadForm() {
    return "videos/create";
  }
}
