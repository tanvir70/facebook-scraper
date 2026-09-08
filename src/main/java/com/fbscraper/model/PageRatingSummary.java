package com.fbscraper.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PageRatingSummary(
        double overallStarRating,
        int ratingCount,
        int yesRecommendations,
        int noRecommendations
) {
    public static final PageRatingSummary EMPTY = new PageRatingSummary(0.0, 0, 0, 0);

    public PageRatingSummary(double overallStarRating, int ratingCount) {
        this(overallStarRating, ratingCount, 0, 0);
    }

    @JsonCreator
    public static PageRatingSummary fromJson(
            @JsonProperty("overallStarRating") double overallStarRating,
            @JsonProperty("ratingCount") int ratingCount,
            @JsonProperty("yesRecommendations") Integer yesRecommendations,
            @JsonProperty("noRecommendations") Integer noRecommendations
    ) {
        return new PageRatingSummary(
                overallStarRating,
                ratingCount,
                yesRecommendations == null ? 0 : yesRecommendations,
                noRecommendations == null ? 0 : noRecommendations
        );
    }

    public boolean hasRatings() {
        return (ratingCount > 0 && overallStarRating > 0.0)
                || (yesRecommendations + noRecommendations > 0);
    }
}
