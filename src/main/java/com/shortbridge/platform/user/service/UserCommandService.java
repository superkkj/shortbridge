package com.shortbridge.platform.user.service;

import com.shortbridge.platform.user.domain.User;
import com.shortbridge.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCommandService {

  private final UserRepository userRepository;

  public User createIfAbsent(String email, String displayName) {
    if (email != null && !email.isBlank()) {
      return userRepository
          .findByEmail(email)
          .orElseGet(() -> userRepository.save(User.builder().email(email).displayName(displayName).build()));
    }
    return userRepository.save(User.builder().email(null).displayName(displayName).build());
  }

  public User save(User user) {
    return userRepository.save(user);
  }
}
