package com.shortbridge.support.security.details;

import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return CurrentUser.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) return null;
    Object principal = authentication.getPrincipal();
    if (principal instanceof CurrentUser cu) return cu;
    if (principal instanceof OAuth2User ou) {
      Object uid = ou.getAttributes().get("userId");
      UUID userId = toUuid(uid);
      if (userId == null) return null;
      Object name = ou.getAttributes().get("displayName");
      Object email = ou.getAttributes().get("email");
      return CurrentUser.of(userId, name == null ? null : name.toString(), email == null ? null : email.toString());
    }
    return null;
  }

  private static UUID toUuid(Object o) {
    if (o == null) return null;
    if (o instanceof UUID u) return u;
    try {
      return UUID.fromString(o.toString());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
