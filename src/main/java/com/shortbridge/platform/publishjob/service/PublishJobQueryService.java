package com.shortbridge.platform.publishjob.service;

import com.shortbridge.platform.publishjob.domain.PublishJob;
import com.shortbridge.platform.publishjob.repository.PublishJobRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublishJobQueryService {

  private final PublishJobRepository publishJobRepository;

  public List<PublishJob> findByPostTargetId(UUID postTargetId) {
    return publishJobRepository.findByPostTargetIdOrderByAttemptAsc(postTargetId);
  }
}
