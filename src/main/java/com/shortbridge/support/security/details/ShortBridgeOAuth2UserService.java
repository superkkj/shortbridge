package com.shortbridge.support.security.details;

import com.shortbridge.platform.loginaccount.domain.LoginProvider;
import com.shortbridge.platform.loginaccount.dto.command.UpsertLoginAccountCommand;
import com.shortbridge.platform.loginaccount.service.LoginAccountFacade;
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
    LoginProvider provider = LoginProvider.fromRegistrationId(registrationId);

    if (provider == null) {
      OAuth2User oauthUser = delegate.loadUser(request);
      log.info("non-login OAuth client: registrationId={}", registrationId);
      return oauthUser;
    }

    OAuth2User oauthUser = delegate.loadUser(request);
    Map<String, Object> attributes = oauthUser.getAttributes();

    UpsertLoginAccountCommand command =
        new UpsertLoginAccountCommand(
            provider,
            provider.providerUserId(attributes),
            provider.email(attributes),
            provider.displayName(attributes),
            attributes);
    User user = loginAccountFacade.upsertOnLogin(command);

    return CurrentUser.of(user.getId(), user.getDisplayName(), user.getEmail());
  }
}
