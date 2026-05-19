package com.shortbridge.platform.loginaccount.dto.command;

import com.shortbridge.platform.loginaccount.domain.LoginProvider;
import java.util.Map;

public record UpsertLoginAccountCommand(
    LoginProvider provider,
    String providerUserId,
    String email,
    String displayName,
    Map<String, Object> rawAttributes) {}
