package com.shortbridge.platform.loginaccount.domain;

import java.util.Map;

/**
 * OAuth 로그인 provider. enum 자체가 attribute 추출을 polymorphism 으로 제공하므로
 * UserService 쪽에 provider-별 switch 가 없다. 새 provider 추가 = enum constant 한 줄 +
 * 추출 메서드 3개 override.
 */
public enum LoginProvider {
  GOOGLE("google") {
    @Override
    public String providerUserId(Map<String, Object> attrs) {
      return str(attrs.get("sub"));
    }

    @Override
    public String email(Map<String, Object> attrs) {
      return str(attrs.get("email"));
    }

    @Override
    public String displayName(Map<String, Object> attrs) {
      return str(attrs.getOrDefault("name", attrs.get("given_name")));
    }
  };

  private final String registrationId;

  LoginProvider(String registrationId) {
    this.registrationId = registrationId;
  }

  public String registrationId() {
    return registrationId;
  }

  public abstract String providerUserId(Map<String, Object> attrs);

  public abstract String email(Map<String, Object> attrs);

  public abstract String displayName(Map<String, Object> attrs);

  public static LoginProvider fromRegistrationId(String registrationId) {
    for (LoginProvider p : values()) {
      if (p.registrationId.equals(registrationId)) {
        return p;
      }
    }
    return null;
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }
}
