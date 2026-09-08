package com.fbscraper.model;


public record PageRatingSummary(
        double overallStarRating,
        int ratingCount
) {
    public static final PageRatingSummary EMPTY = new PageRatingSummary(0.0, 0);

    public boolean hasRatings() {
        return ratingCount > 0 && overallStarRating > 0.0;
    }
}
