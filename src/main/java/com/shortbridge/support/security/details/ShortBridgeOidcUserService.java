package com.shortbridge.support.security.details;

import com.shortbridge.platform.loginaccount.domain.LoginProvider;
import com.shortbridge.platform.loginaccount.dto.command.UpsertLoginAccountCommand;
import com.shortbridge.platform.loginaccount.service.LoginAccountFacade;
import com.shortbridge.platform.user.domain.User;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortBridgeOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private final OidcUserService delegate = new OidcUserService();
  private final LoginAccountFacade loginAccountFacade;

  @Override
  public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
    OidcUser oidcUser = delegate.loadUser(request);
    String registrationId = request.getClientRegistration().getRegistrationId();
    if (!"google".equals(registrationId)) {
      return oidcUser;
    }

    Map<String, Object> attributes = oidcUser.getAttributes();
    String providerUserId = stringOrNull(attributes.get("sub"));
    String email = stringOrNull(attributes.get("email"));
    String displayName = stringOrNull(attributes.getOrDefault("name", email));

    UpsertLoginAccountCommand command =
        new UpsertLoginAccountCommand(LoginProvider.GOOGLE, providerUserId, email, displayName, attributes);
    User user = loginAccountFacade.upsertOnLogin(command);

    Map<String, Object> enriched = new HashMap<>(attributes);
    enriched.put("userId", user.getId());
    enriched.put("displayName", user.getDisplayName() == null ? "" : user.getDisplayName());

    log.info("oidc login success: provider=GOOGLE userId={} email={}", user.getId(), email);
    return new DefaultOidcUser(oidcUser.getAuthorities(), oidcUser.getIdToken(), new OidcUserInfo(enriched), "sub");
  }

  private static String stringOrNull(Object o) {
    return o == null ? null : o.toString();
  }
}
