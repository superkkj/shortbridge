package com.shortbridge.connect.connector;

import java.time.Instant;

public record ConnectionResult(
    String platformUserId,
    String displayName,
    String accessToken,
    String refreshToken,
    Instant expiresAt,
    String scopes,
    String rawProfileJson) {}
