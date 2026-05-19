package com.shortbridge.platform.publishjob.repository;

import com.shortbridge.platform.publishjob.domain.PublishJob;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishJobRepository extends JpaRepository<PublishJob, UUID> {

  List<PublishJob> findByPostTargetIdOrderByAttemptAsc(UUID postTargetId);

  long countByPostTargetId(UUID postTargetId);
}
