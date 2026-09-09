package com.fbscraper.model;

import java.time.Instant;

public record FacebookReview(
        Instant createdTime,
        String recommendationType,
        String reviewText,
        int rating,
        boolean hasReview,
        FacebookUser reviewer,
        SentimentScore score
) {
    public FacebookReview {
        reviewer = reviewer == null ? FacebookUser.ANONYMOUS : reviewer;
    }

    public FacebookReview(
            Instant createdTime,
            String recommendationType,
            String reviewText,
            int rating,
            boolean hasReview,
            FacebookUser reviewer
    ) {
        this(createdTime, recommendationType, reviewText, rating, hasReview, reviewer, null);
    }

    public FacebookReview(
            Instant createdTime,
            String recommendationType,
            String reviewText,
            int rating,
            boolean hasReview
    ) {
        this(createdTime, recommendationType, reviewText, rating, hasReview, FacebookUser.ANONYMOUS, null);
    }

    public boolean isPositiveRecommendation() {
        return "positive".equalsIgnoreCase(recommendationType);
    }

    public boolean isNegativeRecommendation() {
        return "negative".equalsIgnoreCase(recommendationType);
    }
}

