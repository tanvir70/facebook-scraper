package com.fbscraper.model;

import java.time.Instant;

/**
 * Represents a single Facebook comment under a post.
 *
 * @param id          the unique identifier of the comment
 * @param message     the text of the comment
 * @param createdTime the creation timestamp
 */
public record FacebookComment(
        String id,
        String message,
        Instant createdTime
) {}
