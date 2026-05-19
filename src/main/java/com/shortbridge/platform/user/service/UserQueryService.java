package com.shortbridge.platform.user.service;

import com.shortbridge.common.exception.ErrorCode;
import com.shortbridge.common.exception.ShortBridgeException;
import com.shortbridge.platform.user.domain.User;
import com.shortbridge.platform.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserQueryService {

  private final UserRepository userRepository;

  public User get(UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> ShortBridgeException.of(ErrorCode.USER_NOT_FOUND, id));
  }
}
