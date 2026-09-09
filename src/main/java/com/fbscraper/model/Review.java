package com.fbscraper.model;

import java.time.Instant;

public record Review(
        Instant createdTime,
        String recommendationType,
        String reviewText,
        int rating,
        boolean hasReview,
        User reviewer,
        SentimentScore score
) {
    public Review {
        reviewer = reviewer == null ? User.ANONYMOUS : reviewer;
    }

    public Review(
            Instant createdTime,
            String recommendationType,
            String reviewText,
            int rating,
            boolean hasReview,
            User reviewer
    ) {
        this(createdTime, recommendationType, reviewText, rating, hasReview, reviewer, null);
    }

    public Review(
            Instant createdTime,
            String recommendationType,
            String reviewText,
            int rating,
            boolean hasReview
    ) {
        this(createdTime, recommendationType, reviewText, rating, hasReview, User.ANONYMOUS, null);
    }

    public boolean isPositiveRecommendation() {
        return "positive".equalsIgnoreCase(recommendationType);
    }

    public boolean isNegativeRecommendation() {
        return "negative".equalsIgnoreCase(recommendationType);
    }
}

