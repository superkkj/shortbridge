package com.shortbridge.support.ffmpeg;

public record VideoMetadata(
    int durationSeconds, Integer width, Integer height, String videoCodec, String audioCodec) {}
