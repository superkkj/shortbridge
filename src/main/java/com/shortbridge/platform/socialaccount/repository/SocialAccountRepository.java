package com.shortbridge.platform.socialaccount.repository;

import com.shortbridge.platform.socialaccount.domain.Platform;
import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.domain.SocialAccountStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

  @Query(
      "SELECT sa FROM SocialAccount sa WHERE sa.id = :id AND sa.userId = :userId")
  Optional<SocialAccount> findOne(@Param("id") UUID id, @Param("userId") UUID userId);

  List<SocialAccount> findByUserIdOrderByPlatformAsc(UUID userId);

  Optional<SocialAccount> findByUserIdAndPlatformAndStatus(
      UUID userId, Platform platform, SocialAccountStatus status);

  @Query(
      "SELECT sa FROM SocialAccount sa WHERE sa.userId = :userId AND sa.id IN :ids")
  List<SocialAccount> findAllByUserIdAndIds(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);
}
