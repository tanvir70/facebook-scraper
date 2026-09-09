package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record Comment(
        String id,
        String message,
        Instant createdTime,
        ReactionSummary reactions,
        User from,
        List<Comment> replies,
        List<Reaction> userReactions
) {
    public Comment {
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
        from = from == null ? User.ANONYMOUS : from;
        replies = replies == null ? List.of() : List.copyOf(replies);
        userReactions = userReactions == null ? List.of() : List.copyOf(userReactions);
    }

    public Comment(String id, String message, Instant createdTime) {
        this(id, message, createdTime, ReactionSummary.empty(), User.ANONYMOUS, List.of(), List.of());
    }

    public Comment(String id, String message, Instant createdTime, ReactionSummary reactions) {
        this(id, message, createdTime, reactions, User.ANONYMOUS, List.of(), List.of());
    }

    public Comment(String id, String message, Instant createdTime, ReactionSummary reactions, User from) {
        this(id, message, createdTime, reactions, from, List.of(), List.of());
    }
}
