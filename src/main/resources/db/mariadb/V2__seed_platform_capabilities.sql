-- 초기 platform_capabilities seed

INSERT INTO platform_capabilities (id, platform, capability, status, reason_code, reason_message, checked_at, created_at, updated_at)
VALUES
  (UUID(), 'YOUTUBE', 'PUBLIC_PUBLISH', 'PRIVATE_ONLY', 'YOUTUBE_AUDIT_PENDING', '미검증 API project: private upload only', NOW(), NOW(), NOW()),
  (UUID(), 'INSTAGRAM', 'PUBLISH_REELS', 'REVIEW_REQUIRED', 'INSTAGRAM_APP_REVIEW', 'App Review 통과 전 내부 테스트 계정만 허용', NOW(), NOW(), NOW()),
  (UUID(), 'TIKTOK', 'PUBLIC_PUBLISH', 'PRIVATE_ONLY', 'TIKTOK_AUDIT_PENDING', 'Unaudited client: private viewing only', NOW(), NOW(), NOW());

INSERT INTO platform_rate_limits (id, platform, scope_key, limit_count, window_seconds, created_at, updated_at)
VALUES
  (UUID(), 'TIKTOK', 'direct_post_init_per_user_per_minute', 6, 60, NOW(), NOW()),
  (UUID(), 'YOUTUBE', 'videos_insert_per_day', 100, 86400, NOW(), NOW());
