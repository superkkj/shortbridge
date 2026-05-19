package com.shortbridge.platform.socialaccount.controller;

import com.shortbridge.platform.socialaccount.service.SocialAccountFacade;
import com.shortbridge.support.security.details.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/social-accounts")
@RequiredArgsConstructor
public class SocialAccountViewController {

  private final SocialAccountFacade facade;

  @GetMapping
  public String list(CurrentUser currentUser, Model model) {
    model.addAttribute("socialAccounts", facade.list(currentUser.userId()));
    return "social-accounts/list";
  }
}
