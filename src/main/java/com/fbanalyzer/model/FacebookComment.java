package com.fbanalyzer.model;

import java.time.Instant;

public record FacebookComment(
        String id,
        String message,
        Instant createdTime
) {}
