package com.shortbridge.connect.connector;

import java.util.UUID;

public record CallbackContext(UUID userId, String code, String state, String redirectUri) {}
