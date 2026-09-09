package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record FacebookComment(
        String id,
        String message,
        Instant createdTime,
        ReactionSummary reactions,
        FacebookUser from,
        List<FacebookComment> replies,
        List<FacebookReaction> userReactions
) {
    public FacebookComment {
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
        from = from == null ? FacebookUser.ANONYMOUS : from;
        replies = replies == null ? List.of() : List.copyOf(replies);
        userReactions = userReactions == null ? List.of() : List.copyOf(userReactions);
    }

    public FacebookComment(String id, String message, Instant createdTime) {
        this(id, message, createdTime, ReactionSummary.empty(), FacebookUser.ANONYMOUS, List.of(), List.of());
    }

    public FacebookComment(String id, String message, Instant createdTime, ReactionSummary reactions) {
        this(id, message, createdTime, reactions, FacebookUser.ANONYMOUS, List.of(), List.of());
    }

    public FacebookComment(String id, String message, Instant createdTime, ReactionSummary reactions, FacebookUser from) {
        this(id, message, createdTime, reactions, from, List.of(), List.of());
    }
}
