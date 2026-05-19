-- ShortBridge initial schema (PostgreSQL)
-- 1차 설계서 v0.3 기준: UUID PK, FK 없음, JSONB 활용

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255),
    display_name VARCHAR(100),
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX ux_users_email
    ON users(email)
    WHERE email IS NOT NULL;

CREATE TABLE login_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_username VARCHAR(255),
    email VARCHAR(255),
    display_name VARCHAR(255),
    raw_profile_json JSONB,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ux_login_accounts_provider UNIQUE (provider, provider_user_id)
);

CREATE INDEX ix_login_accounts_user_id ON login_accounts(user_id);

CREATE TABLE social_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    platform VARCHAR(30) NOT NULL,
    platform_user_id VARCHAR(255),
    display_name VARCHAR(255),

    access_token_encrypted TEXT,
    refresh_token_encrypted TEXT,
    token_key_version VARCHAR(50),
    token_expires_at TIMESTAMP,
    token_last_refreshed_at TIMESTAMP,
    scopes TEXT,

    status VARCHAR(30) NOT NULL DEFAULT 'CONNECTED',
    raw_profile_json JSONB,
    disconnected_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ux_social_accounts_user_platform_account UNIQUE (user_id, platform, platform_user_id)
);

CREATE INDEX ix_social_accounts_user_id ON social_accounts(user_id);

CREATE TABLE videos (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
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
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    video_id UUID NOT NULL,

    title VARCHAR(255) NOT NULL,
    description TEXT,
    hashtags TEXT,

    publish_mode VARCHAR(30) NOT NULL,
    scheduled_at TIMESTAMP,

    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_posts_user_id_created_at ON posts(user_id, created_at DESC);
CREATE INDEX ix_posts_user_id_scheduled_at ON posts(user_id, scheduled_at);
CREATE INDEX ix_posts_video_id ON posts(video_id);

CREATE TABLE post_targets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    post_id UUID NOT NULL,
    social_account_id UUID NOT NULL,

    platform VARCHAR(30) NOT NULL,

    platform_title VARCHAR(255),
    platform_description TEXT,
    platform_hashtags TEXT,

    privacy_status VARCHAR(30),
    scheduled_at TIMESTAMP,

    status VARCHAR(40) NOT NULL DEFAULT 'READY',
    idempotency_key VARCHAR(120) NOT NULL,

    external_post_id VARCHAR(255),
    external_publish_id VARCHAR(255),
    external_media_id VARCHAR(255),

    error_code VARCHAR(255),
    error_message TEXT,
    raw_response_json JSONB,

    retry_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    queued_at TIMESTAMP,
    locked_at TIMESTAMP,
    locked_by VARCHAR(100),
    published_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT ux_post_targets_post_platform UNIQUE (post_id, platform),
    CONSTRAINT ux_post_targets_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX ix_post_targets_user_platform_status ON post_targets(user_id, platform, status);
CREATE INDEX ix_post_targets_status_scheduled_at ON post_targets(status, scheduled_at);
CREATE INDEX ix_post_targets_status_next_retry_at ON post_targets(status, next_retry_at);

CREATE TABLE publish_jobs (
    id UUID PRIMARY KEY,
    post_target_id UUID NOT NULL,

    platform VARCHAR(30) NOT NULL,
    queue_name VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,

    status VARCHAR(40) NOT NULL,
    attempt INT NOT NULL DEFAULT 0,

    error_code VARCHAR(255),
    error_message TEXT,

    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    locked_at TIMESTAMP,
    locked_by VARCHAR(100),

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT ux_publish_jobs_target_attempt UNIQUE (post_target_id, attempt)
);

CREATE INDEX ix_publish_jobs_post_target_id ON publish_jobs(post_target_id);
CREATE INDEX ix_publish_jobs_status_next_retry_at ON publish_jobs(status, next_retry_at);

CREATE TABLE platform_capabilities (
    id UUID PRIMARY KEY,
    platform VARCHAR(30) NOT NULL,
    capability VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason_code VARCHAR(100),
    reason_message TEXT,
    checked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ux_platform_capabilities_platform_capability UNIQUE (platform, capability)
);

CREATE TABLE platform_rate_limits (
    id UUID PRIMARY KEY,
    platform VARCHAR(30) NOT NULL,
    scope_key VARCHAR(100) NOT NULL,
    limit_count INT NOT NULL,
    window_seconds INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ux_platform_rate_limits_platform_scope UNIQUE (platform, scope_key)
);
