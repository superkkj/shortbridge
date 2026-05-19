package com.shortbridge.support.security.config;

import com.shortbridge.support.security.details.ShortBridgeOAuth2UserService;
import com.shortbridge.support.security.details.ShortBridgeOidcUserService;
import com.shortbridge.support.security.oauth.OAuth2LoginFailureHandler;
import com.shortbridge.support.security.oauth.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final ShortBridgeOAuth2UserService oAuth2UserService;
  private final ShortBridgeOidcUserService oidcUserService;
  private final OAuth2LoginSuccessHandler successHandler;
  private final OAuth2LoginFailureHandler failureHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/login",
                        "/error",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/fonts/**",
                        "/files/**",
                        "/actuator/health",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/dev/login",
                        "/favicon.ico",
                        "/webjars/**")
                    .permitAll()
                    .requestMatchers("/connect/*/callback")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            oauth ->
                oauth.loginPage("/login")
                    .userInfoEndpoint(u -> u.userService(oAuth2UserService).oidcUserService(oidcUserService))
                    .successHandler(successHandler)
                    .failureHandler(failureHandler))
        .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable);

    return http.build();
  }
}
