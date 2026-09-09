package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

/**
 * UI-friendly representation of a Facebook comment and its sentiment result.
 */
public record CommentAnalysis(
        String commentId,
        String postId,
        String postSnippet,
        String message,
        Instant createdTime,
        double compound,
        SentimentLevel level,
        boolean flagged,
        ReactionSummary reactions,
        FacebookUser from,
        List<CommentAnalysis> replies,
        List<FacebookReaction> userReactions
) {
    public CommentAnalysis {
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
        from = from == null ? FacebookUser.ANONYMOUS : from;
        replies = replies == null ? List.of() : List.copyOf(replies);
        userReactions = userReactions == null ? List.of() : List.copyOf(userReactions);
    }

    public CommentAnalysis(
            String commentId,
            String postId,
            String postSnippet,
            String message,
            Instant createdTime,
            double compound,
            SentimentLevel level,
            boolean flagged,
            ReactionSummary reactions
    ) {
        this(commentId, postId, postSnippet, message, createdTime, compound, level, flagged, reactions, FacebookUser.ANONYMOUS, List.of(), List.of());
    }
}
