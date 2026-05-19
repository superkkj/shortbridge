package com.shortbridge.platform.loginaccount.repository;

import com.shortbridge.platform.loginaccount.domain.LoginAccount;
import com.shortbridge.platform.loginaccount.domain.LoginProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAccountRepository extends JpaRepository<LoginAccount, UUID> {

  Optional<LoginAccount> findByProviderAndProviderUserId(LoginProvider provider, String providerUserId);
}
