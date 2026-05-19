package com.shortbridge.platform.post.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdatePostTargetTextRequest(
    @Size(max = 255) String platformTitle,
    @Size(max = 5000) String platformDescription,
    @Size(max = 30) List<@Size(max = 100) String> platformHashtags) {}
