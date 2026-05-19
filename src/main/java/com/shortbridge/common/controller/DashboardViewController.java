package com.shortbridge.common.controller;

import com.shortbridge.platform.post.service.PostFacade;
import com.shortbridge.platform.socialaccount.service.SocialAccountFacade;
import com.shortbridge.platform.video.service.VideoFacade;
import com.shortbridge.support.security.details.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardViewController {

  private final PostFacade postFacade;
  private final VideoFacade videoFacade;
  private final SocialAccountFacade socialAccountFacade;

  @GetMapping("/dashboard")
  public String dashboard(CurrentUser currentUser, Model model) {
    model.addAttribute("posts", postFacade.list(currentUser.userId()));
    model.addAttribute("videos", videoFacade.list(currentUser.userId()));
    model.addAttribute("socialAccounts", socialAccountFacade.list(currentUser.userId()));
    model.addAttribute("user", currentUser);
    return "dashboard/index";
  }
}
