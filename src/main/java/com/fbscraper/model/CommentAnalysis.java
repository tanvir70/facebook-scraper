package com.fbscraper.model;

import java.time.Instant;

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
        ReactionSummary reactions
) {
    public CommentAnalysis {
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
    }
}
