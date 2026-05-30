package com.shortbridge.platform.socialaccount.domain;

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
    name = "social_accounts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "ux_social_accounts_user_platform_account",
            columnNames = {"user_id", "platform", "platform_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SocialAccount extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Platform platform;

  @Column(name = "platform_user_id", length = 255)
  private String platformUserId;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @Column(name = "access_token_encrypted", columnDefinition = "text")
  private String accessTokenEncrypted;

  @Column(name = "refresh_token_encrypted", columnDefinition = "text")
  private String refreshTokenEncrypted;

  @Column(name = "token_key_version", length = 50)
  private String tokenKeyVersion;

  @Column(name = "token_expires_at")
  private Instant tokenExpiresAt;

  @Column(name = "token_last_refreshed_at")
  private Instant tokenLastRefreshedAt;

  @Column(columnDefinition = "text")
  private String scopes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private SocialAccountStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_profile_json", columnDefinition = "jsonb")
  private String rawProfileJson;

  @Column(name = "disconnected_at")
  private Instant disconnectedAt;

  @Builder
  private SocialAccount(
      UUID userId,
      Platform platform,
      String platformUserId,
      String displayName,
      String accessTokenEncrypted,
      String refreshTokenEncrypted,
      String tokenKeyVersion,
      Instant tokenExpiresAt,
      String scopes,
      String rawProfileJson) {
    this.userId = userId;
    this.platform = platform;
    this.platformUserId = platformUserId;
    this.displayName = displayName;
    this.accessTokenEncrypted = accessTokenEncrypted;
    this.refreshTokenEncrypted = refreshTokenEncrypted;
    this.tokenKeyVersion = tokenKeyVersion;
    this.tokenExpiresAt = tokenExpiresAt;
    this.tokenLastRefreshedAt = Instant.now();
    this.scopes = scopes;
    this.rawProfileJson = rawProfileJson;
    this.status = SocialAccountStatus.CONNECTED;
  }

  @PrePersist
  void assignId() {
    if (id == null) id = UUID.randomUUID();
  }

  public void updateToken(
      String accessTokenEncrypted,
      String refreshTokenEncrypted,
      String tokenKeyVersion,
      Instant tokenExpiresAt) {
    this.accessTokenEncrypted = accessTokenEncrypted;
    this.refreshTokenEncrypted = refreshTokenEncrypted;
    this.tokenKeyVersion = tokenKeyVersion;
    this.tokenExpiresAt = tokenExpiresAt;
    this.tokenLastRefreshedAt = Instant.now();
    this.status = SocialAccountStatus.CONNECTED;
  }

  public void updateConnection(
      String platformUserId,
      String displayName,
      String accessTokenEncrypted,
      String refreshTokenEncrypted,
      String tokenKeyVersion,
      Instant tokenExpiresAt,
      String scopes,
      String rawProfileJson) {
    this.platformUserId = platformUserId;
    this.displayName = displayName;
    this.scopes = scopes;
    this.rawProfileJson = rawProfileJson;
    updateToken(accessTokenEncrypted, refreshTokenEncrypted, tokenKeyVersion, tokenExpiresAt);
  }

  public void markTokenExpired() {
    this.status = SocialAccountStatus.TOKEN_EXPIRED;
  }

  public void markReconnectRequired() {
    this.status = SocialAccountStatus.RECONNECT_REQUIRED;
  }

  public void markCapabilityBlocked() {
    this.status = SocialAccountStatus.CAPABILITY_BLOCKED;
  }

  public void disconnect() {
    this.status = SocialAccountStatus.DISCONNECTED;
    this.accessTokenEncrypted = null;
    this.refreshTokenEncrypted = null;
    this.disconnectedAt = Instant.now();
  }
}
