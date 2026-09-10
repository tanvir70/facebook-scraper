package com.fbscraper.model.facebook;

public record FacebookReactionSummary(
        int total,
        int like,
        int love,
        int care,
        int haha,
        int wow,
        int sad,
        int angry
) {
    public static FacebookReactionSummary empty() {
        return new FacebookReactionSummary(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
