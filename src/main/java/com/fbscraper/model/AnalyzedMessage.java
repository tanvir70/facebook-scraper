package com.fbscraper.model;

/**
 * A message enriched with customer sentiment evaluation or Page response classification.
 */
public record AnalyzedMessage(
        FacebookMessage rawMessage,
        boolean isFromPage,
        SentimentScore score,
        boolean flagged
) {}
