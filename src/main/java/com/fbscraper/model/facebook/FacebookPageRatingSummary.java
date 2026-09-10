package com.fbscraper.model.facebook;

public record FacebookPageRatingSummary(
        double overallStarRating,
        int ratingCount,
        int yesRecommendations,
        int noRecommendations
) {
    public static final FacebookPageRatingSummary EMPTY =
            new FacebookPageRatingSummary(0.0, 0, 0, 0);

    public FacebookPageRatingSummary(double overallStarRating, int ratingCount) {
        this(overallStarRating, ratingCount, 0, 0);
    }

    public boolean hasRatings() {
        return ratingCount > 0 || (yesRecommendations + noRecommendations) > 0;
    }
}
