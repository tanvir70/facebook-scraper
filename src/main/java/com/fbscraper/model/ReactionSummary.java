package com.fbscraper.model;

/**
 * Aggregated reaction counts for a Facebook Page post.
 */
public record ReactionSummary(
        int total,
        int like,
        int love,
        int care,
        int haha,
        int wow,
        int sad,
        int angry
) {
    public static ReactionSummary empty() {
        return new ReactionSummary(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
