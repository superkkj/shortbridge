package com.shortbridge.common.storage;

public record StoredObject(String key, long size, String contentType, String checksum) {}
