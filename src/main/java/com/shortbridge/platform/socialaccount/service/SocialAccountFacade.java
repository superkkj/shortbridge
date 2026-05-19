package com.shortbridge.platform.socialaccount.service;

import com.shortbridge.platform.socialaccount.domain.SocialAccount;
import com.shortbridge.platform.socialaccount.dto.response.SocialAccountResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SocialAccountFacade {

  private final SocialAccountQueryService queryService;
  private final SocialAccountCommandService commandService;

  @Transactional(readOnly = true)
  public List<SocialAccountResponse> list(UUID userId) {
    return queryService.list(userId).stream().map(SocialAccountResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public SocialAccountResponse get(UUID userId, UUID id) {
    return SocialAccountResponse.from(queryService.get(userId, id));
  }

  @Transactional
  public void disconnect(UUID userId, UUID id) {
    SocialAccount account = queryService.get(userId, id);
    commandService.disconnect(account);
  }
}
