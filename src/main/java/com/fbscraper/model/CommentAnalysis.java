package com.fbscraper.model;

import com.fbscraper.enums.SentimentLevel;

import java.time.Instant;
import java.util.List;

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
        User from,
        List<CommentAnalysis> replies,
        List<Reaction> userReactions
) {
    public CommentAnalysis {
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
        from = from == null ? User.ANONYMOUS : from;
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
        this(commentId, postId, postSnippet, message, createdTime, compound, level, flagged, reactions, User.ANONYMOUS, List.of(), List.of());
    }
}
