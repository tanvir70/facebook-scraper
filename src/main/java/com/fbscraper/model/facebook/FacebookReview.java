package com.fbscraper.model.facebook;

import com.fbscraper.model.SentimentScore;

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
        hasReview = reviewText != null && !reviewText.isBlank();
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

    public boolean isPositiveRecommendation() {
        return "positive".equalsIgnoreCase(recommendationType);
    }

    public boolean isNegativeRecommendation() {
        return "negative".equalsIgnoreCase(recommendationType);
    }
}
