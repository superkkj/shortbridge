package com.shortbridge.support.security.dev;

import com.shortbridge.platform.user.domain.User;
import com.shortbridge.platform.user.repository.UserRepository;
import com.shortbridge.support.security.details.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
@Profile("local")
@RequiredArgsConstructor
public class DevAuthController {

  private final UserRepository userRepository;

  @GetMapping("/dev/login")
  public String devLogin(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
    User user = userRepository.findAll().stream().findFirst()
        .orElseThrow(() -> new IllegalStateException("No dev user found. Login via Google once."));

    CurrentUser principal = CurrentUser.of(user.getId(), user.getDisplayName(), user.getEmail());

    Authentication auth = new UsernamePasswordAuthenticationToken(
        principal,
        null,
        List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);

    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

    log.info("DEV login: userId={} email={}", user.getId(), user.getEmail());
    return "redirect:/social-accounts";
  }
}
