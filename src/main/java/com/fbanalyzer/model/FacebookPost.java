package com.fbanalyzer.model;

import java.time.Instant;
import java.util.List;

public record FacebookPost(
        String id,
        String message,
        Instant createdTime,
        List<FacebookComment> comments
) {
    public FacebookPost {
        comments = (comments == null) ? List.of() : List.copyOf(comments);
    }
}
