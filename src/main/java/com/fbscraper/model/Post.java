package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record Post(
        String id,
        String message,
        Instant createdTime,
        List<Comment> comments,
        ReactionSummary reactions,
        List<Reaction> userReactions
) {
    public Post {
        comments = (comments == null) ? List.of() : List.copyOf(comments);
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
        userReactions = userReactions == null ? List.of() : List.copyOf(userReactions);
    }

    public Post(String id, String message, Instant createdTime, List<Comment> comments) {
        this(id, message, createdTime, comments, ReactionSummary.empty(), List.of());
    }

    public Post(String id, String message, Instant createdTime, List<Comment> comments, ReactionSummary reactions) {
        this(id, message, createdTime, comments, reactions, List.of());
    }
}
