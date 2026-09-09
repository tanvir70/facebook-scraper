package com.fbscraper.model;

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
