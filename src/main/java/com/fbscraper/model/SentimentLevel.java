package com.fbscraper.model;

/**
 * Categorical classifications for sentiment intensity based on normalized compound scores.
 */
public enum SentimentLevel {
    /** Severe negative sentiment (compound &le; -0.50). Indicates high frustration, churn risk, or service outage. */
    CRITICAL_NEGATIVE,

    /** Moderate negative sentiment (-0.50 &lt; compound &le; -0.05). Indicates minor complaints or dissatisfaction. */
    WARNING_NEGATIVE,

    /** Neutral or informational sentiment (-0.05 &lt; compound &lt; 0.05). */
    NEUTRAL,

    /** Positive sentiment (compound &ge; 0.05). Indicates satisfaction, praise, or enthusiasm. */
    POSITIVE
}
