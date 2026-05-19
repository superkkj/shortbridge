package com.shortbridge.support.security.details;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

public record CurrentUser(
    UUID userId,
    String displayName,
    String email,
    Collection<SimpleGrantedAuthority> authorities,
    Map<String, Object> attributes)
    implements UserDetails, OAuth2User {

  public static CurrentUser of(UUID userId, String displayName, String email) {
    return new CurrentUser(
        userId,
        displayName,
        email,
        java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")),
        Map.of("userId", userId, "email", email == null ? "" : email));
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public String getUsername() {
    return userId.toString();
  }

  @Override
  public String getName() {
    return userId.toString();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
