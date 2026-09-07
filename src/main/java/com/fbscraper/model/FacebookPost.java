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
 */
public record FacebookPost(
        String id,
        String message,
        Instant createdTime,
        List<FacebookComment> comments
) {
    public FacebookPost {
        comments = (comments == null) ? List.of() : List.copyOf(comments);
    }
}
