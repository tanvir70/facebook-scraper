package com.fbscraper.model.facebook;

import java.time.Instant;
import java.util.List;

public record FacebookPost(
        String id,
        String message,
        Instant createdTime,
        List<FacebookComment> comments,
        FacebookReactionSummary reactions,
        List<FacebookReaction> userReactions
) {
    public FacebookPost {
        comments = (comments == null) ? List.of() : List.copyOf(comments);
        reactions = reactions == null ? FacebookReactionSummary.empty() : reactions;
        userReactions = userReactions == null ? List.of() : List.copyOf(userReactions);
    }

    public FacebookPost(String id, String message, Instant createdTime, List<FacebookComment> comments) {
        this(id, message, createdTime, comments, FacebookReactionSummary.empty(), List.of());
    }

    public FacebookPost(String id, String message, Instant createdTime, List<FacebookComment> comments, FacebookReactionSummary reactions) {
        this(id, message, createdTime, comments, reactions, List.of());
    }
}
