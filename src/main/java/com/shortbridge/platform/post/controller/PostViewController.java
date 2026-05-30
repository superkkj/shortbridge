package com.shortbridge.platform.post.controller;

import com.shortbridge.platform.post.service.PostFacade;
import com.shortbridge.platform.socialaccount.service.SocialAccountFacade;
import com.shortbridge.platform.video.service.VideoFacade;
import com.shortbridge.support.security.details.CurrentUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostViewController {

  private final PostFacade postFacade;
  private final VideoFacade videoFacade;
  private final SocialAccountFacade socialAccountFacade;

  @GetMapping
  public String list(
      CurrentUser currentUser,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
      Model model) {
    model.addAttribute("postPage", postFacade.list(currentUser.userId(), pageable));
    return "posts/list";
  }

  @GetMapping("/new")
  public String createForm(CurrentUser currentUser, Model model) {
    model.addAttribute("videos", videoFacade.list(currentUser.userId()));
    model.addAttribute("socialAccounts", socialAccountFacade.listConnected(currentUser.userId()));
    return "posts/create";
  }

  @GetMapping("/{id}")
  public String detail(CurrentUser currentUser, @PathVariable UUID id, Model model) {
    model.addAttribute("post", postFacade.get(currentUser.userId(), id));
    return "posts/detail";
  }
}
