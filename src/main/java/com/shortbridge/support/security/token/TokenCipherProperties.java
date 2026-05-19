package com.shortbridge.support.security.token;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortbridge.security.token-cipher")
public record TokenCipherProperties(Map<String, String> keys, String activeKeyVersion) {}
