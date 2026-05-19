package com.shortbridge.platform.video.repository;

import com.shortbridge.platform.video.domain.Video;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, UUID> {

  @Query(
      "SELECT v FROM Video v WHERE v.id = :id AND v.userId = :userId AND v.status <> com.shortbridge.platform.video.domain.VideoStatus.DELETED")
  Optional<Video> findOne(@Param("id") UUID id, @Param("userId") UUID userId);

  List<Video> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
