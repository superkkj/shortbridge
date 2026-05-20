package com.shortbridge.platform.loginaccount.domain;

import com.shortbridge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "login_accounts",
    uniqueConstraints = @UniqueConstraint(name = "ux_login_accounts_provider", columnNames = {"provider", "provider_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LoginAccount extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private LoginProvider provider;

  @Column(name = "provider_user_id", nullable = false, length = 255)
  private String providerUserId;

  @Column(name = "provider_username", length = 255)
  private String providerUsername;

  @Column(length = 255)
  private String email;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_profile_json", columnDefinition = "jsonb")
  private String rawProfileJson;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Builder
  private LoginAccount(
      UUID userId,
      LoginProvider provider,
      String providerUserId,
      String providerUsername,
      String email,
      String displayName,
      String rawProfileJson) {
    this.userId = userId;
    this.provider = provider;
    this.providerUserId = providerUserId;
    this.providerUsername = providerUsername;
    this.email = email;
    this.displayName = displayName;
    this.rawProfileJson = rawProfileJson;
  }

  @PrePersist
  void assignId() {
    if (id == null) id = UUID.randomUUID();
  }

  public void markLogin(Instant loggedInAt) {
    this.lastLoginAt = loggedInAt;
  }

  public void updateProfile(String email, String displayName, String rawProfileJson) {
    if (email != null && !email.isBlank()) this.email = email;
    if (displayName != null && !displayName.isBlank()) this.displayName = displayName;
    if (rawProfileJson != null) this.rawProfileJson = rawProfileJson;
  }
}
