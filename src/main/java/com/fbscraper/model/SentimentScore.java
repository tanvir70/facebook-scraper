package com.fbscraper.model;

/**
 * Represents the multi-dimensional sentiment metrics evaluated for a text message.
 *
 * @param compound normalized unidimensional composite score (-1.0 to +1.0)
 * @param positive ratio of positive valence words (0.0 to 1.0)
 * @param neutral  ratio of neutral valence words (0.0 to 1.0)
 * @param negative ratio of negative valence words (0.0 to 1.0)
 * @param level    categorical intensity level classification
 */
public record SentimentScore(
        double compound,
        double positive,
        double neutral,
        double negative,
        SentimentLevel level
) {
    /**
     * Checks if this sentiment score is considered negative (either WARNING or CRITICAL).
     *
     * @return true if the sentiment level represents a negative classification
     */
    public boolean isNegative() {
        return level == SentimentLevel.CRITICAL_NEGATIVE || level == SentimentLevel.WARNING_NEGATIVE;
    }
}
