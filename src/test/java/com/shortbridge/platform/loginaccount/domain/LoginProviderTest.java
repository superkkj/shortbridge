package com.shortbridge.platform.loginaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoginProvider (polymorphic OAuth attribute extraction)")
class LoginProviderTest {

  @Test
  void fromRegistrationId_google_returnsGoogle() {
    assertThat(LoginProvider.fromRegistrationId("google")).isEqualTo(LoginProvider.GOOGLE);
  }

  @Test
  void fromRegistrationId_unknown_returnsNull() {
    assertThat(LoginProvider.fromRegistrationId("apple")).isNull();
    assertThat(LoginProvider.fromRegistrationId(null)).isNull();
  }

  @Test
  void google_providerUserId_readsSubAttribute() {
    Map<String, Object> attrs = Map.of("sub", "1234567890", "email", "x@y.com");
    assertThat(LoginProvider.GOOGLE.providerUserId(attrs)).isEqualTo("1234567890");
  }

  @Test
  void google_email_readsEmailAttribute() {
    Map<String, Object> attrs = Map.of("email", "wade@example.com");
    assertThat(LoginProvider.GOOGLE.email(attrs)).isEqualTo("wade@example.com");
  }

  @Test
  void google_displayName_prefersName_thenGivenName() {
    assertThat(LoginProvider.GOOGLE.displayName(Map.of("name", "Wade"))).isEqualTo("Wade");
    assertThat(LoginProvider.GOOGLE.displayName(Map.of("given_name", "Wade"))).isEqualTo("Wade");
  }

  @Test
  void google_missingAttributes_returnsNull() {
    Map<String, Object> empty = new HashMap<>();
    assertThat(LoginProvider.GOOGLE.providerUserId(empty)).isNull();
    assertThat(LoginProvider.GOOGLE.email(empty)).isNull();
    assertThat(LoginProvider.GOOGLE.displayName(empty)).isNull();
  }

  @Test
  @DisplayName("모든 provider 가 registrationId() 를 비어있지 않게 반환 (drift 방지)")
  void every_provider_has_registrationId() {
    for (LoginProvider p : LoginProvider.values()) {
      assertThat(p.registrationId()).isNotBlank();
    }
  }
}
