package com.shortbridge.platform.post.repository;

import com.shortbridge.platform.post.domain.Post;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, UUID> {

  @Query("SELECT p FROM Post p WHERE p.id = :id AND p.userId = :userId")
  Optional<Post> findOne(@Param("id") UUID id, @Param("userId") UUID userId);

  List<Post> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
