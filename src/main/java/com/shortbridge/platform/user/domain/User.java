package com.shortbridge.platform.user.domain;

import com.shortbridge.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends BaseEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(length = 255)
  private String email;

  @Column(name = "display_name", length = 100)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private UserStatus status;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Builder
  private User(String email, String displayName, UserRole role, UserStatus status) {
    this.email = email;
    this.displayName = displayName;
    this.role = role == null ? UserRole.USER : role;
    this.status = status == null ? UserStatus.ACTIVE : status;
  }

  @PrePersist
  void assignId() {
    if (id == null) id = UUID.randomUUID();
  }

  public void markLogin(Instant loggedInAt) {
    this.lastLoginAt = loggedInAt;
  }

  public void updateProfile(String email, String displayName) {
    if (email != null && !email.isBlank()) this.email = email;
    if (displayName != null && !displayName.isBlank()) this.displayName = displayName;
  }

  public void suspend() {
    this.status = UserStatus.SUSPENDED;
  }

  public void withdraw() {
    this.status = UserStatus.WITHDRAWN;
  }
}
