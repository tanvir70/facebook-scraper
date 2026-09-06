package com.fbanalyzer.model;

public record SentimentScore(
        double compound,
        double positive,
        double neutral,
        double negative,
        SentimentLevel level
) {
    public boolean isNegative() {
        return level == SentimentLevel.CRITICAL_NEGATIVE || level == SentimentLevel.WARNING_NEGATIVE;
    }
}
