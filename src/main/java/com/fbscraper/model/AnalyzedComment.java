package com.fbscraper.model;

/**
 * Represents a comment enriched with sentiment scoring and parent post context.
 *
 * @param comment     the underlying {@link FacebookComment}
 * @param postId      the ID of the parent post
 * @param postSnippet preview snippet of the parent post's message
 * @param score       the calculated {@link SentimentScore}
 */
public record AnalyzedComment(
        FacebookComment comment,
        String postId,
        String postSnippet,
        SentimentScore score
) {}
