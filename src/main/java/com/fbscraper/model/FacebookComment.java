package com.fbscraper.model;

import java.time.Instant;

/**
 * Represents a single Facebook comment under a post.
 *
 * @param id          the unique identifier of the comment
 * @param message     the text of the comment
 * @param createdTime the creation timestamp
 * @param reactions   aggregated reaction counts for this comment
 */
public record FacebookComment(
        String id,
        String message,
        Instant createdTime,
        ReactionSummary reactions
) {
    public FacebookComment {
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
    }

    public FacebookComment(String id, String message, Instant createdTime) {
        this(id, message, createdTime, ReactionSummary.empty());
    }
}
