-- ShortBridge initial schema (MariaDB)

CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    email VARCHAR(255),
    display_name VARCHAR(100),
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY ux_users_email (email)
);

CREATE TABLE login_accounts (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_username VARCHAR(255),
    email VARCHAR(255),
    display_name VARCHAR(255),
    raw_profile_json LONGTEXT,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY ux_login_accounts_provider (provider, provider_user_id)
);

CREATE INDEX ix_login_accounts_user_id ON login_accounts(user_id);

CREATE TABLE social_accounts (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    platform_user_id VARCHAR(255),
    display_name VARCHAR(255),

    access_token_encrypted TEXT,
    refresh_token_encrypted TEXT,
    token_key_version VARCHAR(50),
    token_expires_at TIMESTAMP NULL,
    token_last_refreshed_at TIMESTAMP NULL,
    scopes TEXT,

    status VARCHAR(30) NOT NULL DEFAULT 'CONNECTED',
    raw_profile_json LONGTEXT,
    disconnected_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY ux_social_accounts_user_platform_account (user_id, platform, platform_user_id)
);

CREATE INDEX ix_social_accounts_user_id ON social_accounts(user_id);

CREATE TABLE videos (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    duration_sec INT,
    width INT,
    height INT,
    video_codec VARCHAR(100),
    audio_codec VARCHAR(100),
    aspect_ratio VARCHAR(20),
    checksum VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_videos_user_id_created_at ON videos(user_id, created_at DESC);

CREATE TABLE posts (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    video_id CHAR(36) NOT NULL,

    title VARCHAR(255) NOT NULL,
    description TEXT,
    hashtags TEXT,

    publish_mode VARCHAR(30) NOT NULL,
    scheduled_at TIMESTAMP NULL,

    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_posts_user_id_created_at ON posts(user_id, created_at DESC);
CREATE INDEX ix_posts_user_id_scheduled_at ON posts(user_id, scheduled_at);
CREATE INDEX ix_posts_video_id ON posts(video_id);

CREATE TABLE post_targets (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    post_id CHAR(36) NOT NULL,
    social_account_id CHAR(36) NOT NULL,

    platform VARCHAR(30) NOT NULL,

    platform_title VARCHAR(255),
    platform_description TEXT,
    platform_hashtags TEXT,

    privacy_status VARCHAR(30),
    scheduled_at TIMESTAMP NULL,

    status VARCHAR(40) NOT NULL DEFAULT 'READY',
    idempotency_key VARCHAR(120) NOT NULL,

    external_post_id VARCHAR(255),
    external_publish_id VARCHAR(255),
    external_media_id VARCHAR(255),

    error_code VARCHAR(255),
    error_message TEXT,
    raw_response_json LONGTEXT,

    retry_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP NULL,
    next_retry_at TIMESTAMP NULL,
    queued_at TIMESTAMP NULL,
    locked_at TIMESTAMP NULL,
    locked_by VARCHAR(100),
    published_at TIMESTAMP NULL,

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    UNIQUE KEY ux_post_targets_post_platform (post_id, platform),
    UNIQUE KEY ux_post_targets_idempotency_key (idempotency_key)
);

CREATE INDEX ix_post_targets_user_platform_status ON post_targets(user_id, platform, status);
CREATE INDEX ix_post_targets_status_scheduled_at ON post_targets(status, scheduled_at);
CREATE INDEX ix_post_targets_status_next_retry_at ON post_targets(status, next_retry_at);

CREATE TABLE publish_jobs (
    id CHAR(36) PRIMARY KEY,
    post_target_id CHAR(36) NOT NULL,

    platform VARCHAR(30) NOT NULL,
    queue_name VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,

    status VARCHAR(40) NOT NULL,
    attempt INT NOT NULL DEFAULT 0,

    error_code VARCHAR(255),
    error_message TEXT,

    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    next_retry_at TIMESTAMP NULL,
    locked_at TIMESTAMP NULL,
    locked_by VARCHAR(100),

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    UNIQUE KEY ux_publish_jobs_target_attempt (post_target_id, attempt)
);

CREATE INDEX ix_publish_jobs_post_target_id ON publish_jobs(post_target_id);
CREATE INDEX ix_publish_jobs_status_next_retry_at ON publish_jobs(status, next_retry_at);

CREATE TABLE platform_capabilities (
    id CHAR(36) PRIMARY KEY,
    platform VARCHAR(30) NOT NULL,
    capability VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason_code VARCHAR(100),
    reason_message TEXT,
    checked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY ux_platform_capabilities_platform_capability (platform, capability)
);

CREATE TABLE platform_rate_limits (
    id CHAR(36) PRIMARY KEY,
    platform VARCHAR(30) NOT NULL,
    scope_key VARCHAR(100) NOT NULL,
    limit_count INT NOT NULL,
    window_seconds INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY ux_platform_rate_limits_platform_scope (platform, scope_key)
);
