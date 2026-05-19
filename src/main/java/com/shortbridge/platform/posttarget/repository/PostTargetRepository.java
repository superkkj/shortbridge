package com.shortbridge.platform.posttarget.repository;

import com.shortbridge.platform.posttarget.domain.PostTarget;
import com.shortbridge.platform.posttarget.domain.PostTargetStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostTargetRepository extends JpaRepository<PostTarget, UUID> {

  @Query("SELECT pt FROM PostTarget pt WHERE pt.id = :id AND pt.userId = :userId")
  Optional<PostTarget> findOne(@Param("id") UUID id, @Param("userId") UUID userId);

  List<PostTarget> findByPostIdAndUserIdOrderByPlatformAsc(UUID postId, UUID userId);

  @Query(
      "SELECT pt FROM PostTarget pt WHERE pt.status = com.shortbridge.platform.posttarget.domain.PostTargetStatus.READY "
          + "AND pt.scheduledAt IS NOT NULL AND pt.scheduledAt <= :now ORDER BY pt.scheduledAt ASC")
  List<PostTarget> findDueReadyTargets(@Param("now") Instant now);

  @Query(
      "SELECT pt FROM PostTarget pt WHERE pt.status = com.shortbridge.platform.posttarget.domain.PostTargetStatus.RETRY_WAIT "
          + "AND pt.nextRetryAt IS NOT NULL AND pt.nextRetryAt <= :now ORDER BY pt.nextRetryAt ASC")
  List<PostTarget> findDueRetryTargets(@Param("now") Instant now);

  @Modifying
  @Query(
      "UPDATE PostTarget pt SET pt.status = com.shortbridge.platform.posttarget.domain.PostTargetStatus.LOCKED, "
          + "pt.lockedAt = :now, pt.lockedBy = :workerId "
          + "WHERE pt.id = :id AND pt.status IN :allowed")
  int lockForPublish(
      @Param("id") UUID id,
      @Param("workerId") String workerId,
      @Param("now") Instant now,
      @Param("allowed") List<PostTargetStatus> allowed);

  @Modifying
  @Query(
      "UPDATE PostTarget pt SET pt.status = com.shortbridge.platform.posttarget.domain.PostTargetStatus.QUEUED, "
          + "pt.queuedAt = :now WHERE pt.id = :id AND pt.status = com.shortbridge.platform.posttarget.domain.PostTargetStatus.READY")
  int markQueuedConditional(@Param("id") UUID id, @Param("now") Instant now);
}
