-- X (Twitter) 플랫폼 지원 제거 (Pay-per-use 모델 도입으로 보류)
DELETE FROM post_targets WHERE platform = 'TWITTER';
DELETE FROM social_accounts WHERE platform = 'TWITTER';
DELETE FROM platform_capabilities WHERE platform = 'TWITTER';
DELETE FROM platform_rate_limits WHERE platform = 'TWITTER';
