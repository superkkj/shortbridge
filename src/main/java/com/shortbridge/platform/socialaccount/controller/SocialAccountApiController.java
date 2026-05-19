package com.shortbridge.platform.socialaccount.controller;

import com.shortbridge.platform.socialaccount.dto.response.SocialAccountResponse;
import com.shortbridge.platform.socialaccount.service.SocialAccountFacade;
import com.shortbridge.support.security.details.CurrentUser;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/social-accounts")
@RequiredArgsConstructor
public class SocialAccountApiController {

  private final SocialAccountFacade facade;

  @GetMapping
  public List<SocialAccountResponse> list(CurrentUser currentUser) {
    return facade.list(currentUser.userId());
  }

  @GetMapping("/{id}")
  public SocialAccountResponse get(CurrentUser currentUser, @PathVariable UUID id) {
    return facade.get(currentUser.userId(), id);
  }

  @DeleteMapping("/{id}")
  public void disconnect(CurrentUser currentUser, @PathVariable UUID id) {
    facade.disconnect(currentUser.userId(), id);
  }
}
