package com.fbscraper.model;

import java.time.Instant;

public record FacebookReview(
        Instant createdTime,
        String recommendationType,
        String reviewText,
        int rating,
        boolean hasReview) {

    public boolean isPositiveRecommendation() {
        return "positive".equalsIgnoreCase(recommendationType);
    }

    public boolean isNegativeRecommendation() {
        return "negative".equalsIgnoreCase(recommendationType);
    }
}
