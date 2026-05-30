package com.shortbridge.platform.socialaccount.controller;

import com.shortbridge.connect.connector.ConnectorProperties;
import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccountStatus;
import com.shortbridge.platform.socialaccount.dto.response.SocialAccountResponse;
import com.shortbridge.platform.socialaccount.service.SocialAccountFacade;
import com.shortbridge.support.security.details.CurrentUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/social-accounts")
@RequiredArgsConstructor
@EnableConfigurationProperties(ConnectorProperties.class)
public class SocialAccountViewController {

  private final SocialAccountFacade facade;
  private final ConnectorProperties connectorProperties;

  @GetMapping
  public String list(CurrentUser currentUser, Model model) {
    List<SocialAccountResponse> socialAccounts = facade.list(currentUser.userId());
    model.addAttribute("socialAccounts", socialAccounts);
    model.addAttribute("instagramConfigured", isConfigured(connectorProperties.instagram()));
    model.addAttribute("instagramRedirectUri", connectorProperties.redirectUri(Platform.INSTAGRAM));
    model.addAttribute("tiktokCapabilityBlocked", hasCapabilityBlockedTikTok(socialAccounts));
    return "social-accounts/list";
  }

  private static boolean hasCapabilityBlockedTikTok(List<SocialAccountResponse> socialAccounts) {
    return socialAccounts.stream()
        .anyMatch(
            account ->
                account.platform() == Platform.TIKTOK
                    && account.status() == SocialAccountStatus.CAPABILITY_BLOCKED);
  }

  private static boolean isConfigured(ConnectorProperties.Provider provider) {
    return provider != null
        && hasRealValue(provider.clientId())
        && hasRealValue(provider.clientSecret());
  }

  private static boolean hasRealValue(String value) {
    return value != null
        && !value.isBlank()
        && !"REPLACE_ME".equals(value)
        && !"not-set".equals(value)
        && !"not-set-yet".equals(value);
  }
}
