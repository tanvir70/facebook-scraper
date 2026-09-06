package com.fbanalyzer.model;

public record AnalyzedComment(
        FacebookComment comment,
        String postId,
        String postSnippet,
        SentimentScore score
) {}
