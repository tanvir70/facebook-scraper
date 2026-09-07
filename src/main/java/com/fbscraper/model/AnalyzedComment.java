package com.fbscraper.model;

public record AnalyzedComment(
        FacebookComment comment,
        String postId,
        String postSnippet,
        SentimentScore score
) {}
