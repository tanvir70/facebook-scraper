package com.fbscraper.model;

public record AnalyzedMessage(
        FacebookMessage rawMessage,
        boolean isFromPage,
        SentimentScore score,
        boolean flagged
) {}
