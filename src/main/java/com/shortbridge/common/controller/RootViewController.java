package com.shortbridge.common.controller;

import com.shortbridge.support.security.details.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootViewController {

  @GetMapping("/")
  public String root(CurrentUser currentUser) {
    return currentUser == null ? "redirect:/login" : "redirect:/dashboard";
  }

  @GetMapping("/login")
  public String login() {
    return "auth/login";
  }
}
