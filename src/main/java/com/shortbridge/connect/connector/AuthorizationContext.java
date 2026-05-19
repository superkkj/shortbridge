package com.shortbridge.connect.connector;

import java.util.UUID;

public record AuthorizationContext(UUID userId, String state, String redirectUri) {}
