package com.fbscraper.model;

import java.time.Instant;

/**
 * Represents a single Facebook comment under a post.
 *
 * @param id          the unique identifier of the comment
 * @param message     the text of the comment
 * @param createdTime the creation timestamp
 * @param author      the non-sensitive profile fields Meta exposes for the commenter
 * @param reactions   aggregated reaction counts for this comment
 */
public record FacebookComment(
        String id,
        String message,
        Instant createdTime,
        CommentAuthor author,
        ReactionSummary reactions
) {
    public FacebookComment {
        author = author == null ? CommentAuthor.UNKNOWN : author;
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
    }

    public FacebookComment(String id, String message, Instant createdTime) {
        this(id, message, createdTime, CommentAuthor.UNKNOWN, ReactionSummary.empty());
    }

    public FacebookComment(String id, String message, Instant createdTime, ReactionSummary reactions) {
        this(id, message, createdTime, CommentAuthor.UNKNOWN, reactions);
    }
}
