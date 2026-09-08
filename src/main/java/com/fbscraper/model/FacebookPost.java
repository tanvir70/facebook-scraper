package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents a Facebook Page post.
 *
 * @param id          the unique identifier of the post
 * @param message     the text content of the post
 * @param createdTime the creation timestamp
 * @param comments    the list of comments associated with this post (defensively copied)
 * @param reactions   aggregated reaction counts for this post
 */
public record FacebookPost(
        String id,
        String message,
        Instant createdTime,
        List<FacebookComment> comments,
        ReactionSummary reactions
) {
    public FacebookPost {
        comments = (comments == null) ? List.of() : List.copyOf(comments);
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
    }
}
