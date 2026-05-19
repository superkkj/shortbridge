package com.shortbridge.platform.loginaccount.service;

import com.shortbridge.common.util.JsonUtils;
import com.shortbridge.platform.loginaccount.domain.LoginAccount;
import com.shortbridge.platform.loginaccount.dto.command.UpsertLoginAccountCommand;
import com.shortbridge.platform.loginaccount.repository.LoginAccountRepository;
import com.shortbridge.platform.user.domain.User;
import com.shortbridge.platform.user.service.UserCommandService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LoginAccountFacade {

  private final LoginAccountRepository loginAccountRepository;
  private final UserCommandService userCommandService;

  @Transactional
  public User upsertOnLogin(UpsertLoginAccountCommand command) {
    String rawProfileJson = command.rawAttributes() == null ? null : JsonUtils.write(command.rawAttributes());
    LoginAccount existing =
        loginAccountRepository
            .findByProviderAndProviderUserId(command.provider(), command.providerUserId())
            .orElse(null);

    Instant now = Instant.now();
    User user;
    if (existing == null) {
      user = userCommandService.createIfAbsent(command.email(), command.displayName());
      LoginAccount account =
          LoginAccount.builder()
              .userId(user.getId())
              .provider(command.provider())
              .providerUserId(command.providerUserId())
              .providerUsername(command.displayName())
              .email(command.email())
              .displayName(command.displayName())
              .rawProfileJson(rawProfileJson)
              .build();
      account.markLogin(now);
      loginAccountRepository.save(account);
    } else {
      existing.updateProfile(command.email(), command.displayName(), rawProfileJson);
      existing.markLogin(now);
      user = userCommandService.save(loadOrUpdate(existing, command));
    }
    user.markLogin(now);
    return user;
  }

  private User loadOrUpdate(LoginAccount existing, UpsertLoginAccountCommand command) {
    User user = userCommandService.createIfAbsent(command.email(), command.displayName());
    if (!user.getId().equals(existing.getUserId())) {
      throw new IllegalStateException("loginAccount.userId mismatch with email-based user");
    }
    user.updateProfile(command.email(), command.displayName());
    return user;
  }
}
