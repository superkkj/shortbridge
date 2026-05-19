package com.shortbridge.support.security.details;

import com.shortbridge.platform.loginaccount.domain.LoginProvider;
import com.shortbridge.platform.loginaccount.service.LoginAccountFacade;
import com.shortbridge.platform.loginaccount.dto.command.UpsertLoginAccountCommand;
import com.shortbridge.platform.user.domain.User;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortBridgeOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
  private final LoginAccountFacade loginAccountFacade;

  @Override
  public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
    String registrationId = request.getClientRegistration().getRegistrationId();
    LoginProvider provider = resolveProvider(registrationId);

    if (provider == null) {
      OAuth2User oauthUser = delegate.loadUser(request);
      log.info("non-login OAuth client: registrationId={}", registrationId);
      return oauthUser;
    }

    OAuth2User oauthUser = delegate.loadUser(request);
    Map<String, Object> attributes = oauthUser.getAttributes();
    String providerUserId = extractProviderUserId(provider, attributes);
    String email = extractEmail(provider, attributes);
    String displayName = extractDisplayName(provider, attributes);

    UpsertLoginAccountCommand command =
        new UpsertLoginAccountCommand(provider, providerUserId, email, displayName, attributes);
    User user = loginAccountFacade.upsertOnLogin(command);

    return CurrentUser.of(user.getId(), user.getDisplayName(), user.getEmail());
  }

  private LoginProvider resolveProvider(String registrationId) {
    return switch (registrationId) {
      case "google" -> LoginProvider.GOOGLE;
      default -> null;
    };
  }

  private String extractProviderUserId(LoginProvider provider, Map<String, Object> attributes) {
    return switch (provider) {
      case GOOGLE -> stringOrNull(attributes.get("sub"));
    };
  }

  private String extractEmail(LoginProvider provider, Map<String, Object> attributes) {
    return switch (provider) {
      case GOOGLE -> stringOrNull(attributes.get("email"));
    };
  }

  private String extractDisplayName(LoginProvider provider, Map<String, Object> attributes) {
    return switch (provider) {
      case GOOGLE -> stringOrNull(attributes.getOrDefault("name", attributes.get("given_name")));
    };
  }

  private static String stringOrNull(Object o) {
    return o == null ? null : o.toString();
  }
}
