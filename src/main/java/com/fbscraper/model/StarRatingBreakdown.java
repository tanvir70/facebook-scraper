package com.fbscraper.model;

import java.util.List;

/**
 * Encapsulates a 5-star rating summary derived from analyzing sentiment across comments and reviews.
 *
 * @param averageRating  weighted average score on a 1.0 to 5.0 scale
 * @param totalCount     total feedback items evaluated (comments + reviews)
 * @param fiveStarCount  count of strong positive items (compound &ge; 0.50) or 5-star reviews
 * @param fourStarCount  count of moderate positive items (0.05 &le; compound &lt; 0.50) or 4-star reviews
 * @param threeStarCount count of neutral items (-0.05 &lt; compound &lt; 0.05) or 3-star reviews
 * @param twoStarCount   count of warning negative items (-0.50 &lt; compound &le; -0.05) or 2-star reviews
 * @param oneStarCount   count of critical negative items (compound &le; -0.50) or 1-star reviews
 */
public record StarRatingBreakdown(
        double averageRating,
        int totalCount,
        int fiveStarCount,
        int fourStarCount,
        int threeStarCount,
        int twoStarCount,
        int oneStarCount
) {
    public static final StarRatingBreakdown EMPTY = new StarRatingBreakdown(0.0, 0, 0, 0, 0, 0, 0);

    public boolean hasRatings() {
        return totalCount > 0 && averageRating > 0.0;
    }

    public int totalPositive() {
        return fiveStarCount + fourStarCount;
    }

    public int totalNegative() {
        return twoStarCount + oneStarCount;
    }

    public double fiveStarPercent() { return percent(fiveStarCount); }
    public double fourStarPercent() { return percent(fourStarCount); }
    public double threeStarPercent() { return percent(threeStarCount); }
    public double twoStarPercent() { return percent(twoStarCount); }
    public double oneStarPercent() { return percent(oneStarCount); }

    public double positivePercent() { return percent(totalPositive()); }
    public double negativePercent() { return percent(totalNegative()); }

    private double percent(int count) {
        return totalCount > 0 ? ((double) count / totalCount) * 100.0 : 0.0;
    }

    /**
     * Formats the average rating into a 5-star graphical representation (e.g., ★★★★☆).
     */
    public String formattedStars() {
        int rounded = (int) Math.round(averageRating);
        return "★".repeat(Math.max(0, Math.min(5, rounded))) + "☆".repeat(Math.max(0, 5 - Math.max(0, Math.min(5, rounded))));
    }

    /**
     * Computes the 5-star rating breakdown across all analyzed comments and reviews.
     */
    public static StarRatingBreakdown compute(List<AnalyzedComment> comments, List<AnalyzedReview> reviews) {
        int c5 = 0, c4 = 0, c3 = 0, c2 = 0, c1 = 0;

        if (comments != null) {
            for (AnalyzedComment c : comments) {
                int stars = mapScoreToStars(c.score());
                switch (stars) {
                    case 5 -> c5++;
                    case 4 -> c4++;
                    case 3 -> c3++;
                    case 2 -> c2++;
                    case 1 -> c1++;
                }
            }
        }

        if (reviews != null) {
            for (AnalyzedReview r : reviews) {
                int stars = mapReviewToStars(r);
                switch (stars) {
                    case 5 -> c5++;
                    case 4 -> c4++;
                    case 3 -> c3++;
                    case 2 -> c2++;
                    case 1 -> c1++;
                }
            }
        }

        int total = c5 + c4 + c3 + c2 + c1;
        if (total == 0) {
            return EMPTY;
        }

        double weightedSum = (c5 * 5.0) + (c4 * 4.0) + (c3 * 3.0) + (c2 * 2.0) + (c1 * 1.0);
        double avg = Math.round((weightedSum / total) * 10.0) / 10.0;

        return new StarRatingBreakdown(avg, total, c5, c4, c3, c2, c1);
    }

    /**
     * Maps a VADER sentiment score into a 1 to 5 star rating.
     */
    public static int mapScoreToStars(SentimentScore score) {
        if (score == null) return 3;
        double compound = score.compound();
        if (compound >= 0.50) return 5;
        if (compound >= 0.05) return 4;
        if (compound > -0.05) return 3;
        if (compound > -0.50) return 2;
        return 1;
    }

    /**
     * Resolves the 1 to 5 star rating for a customer review.
     */
    public static int mapReviewToStars(AnalyzedReview review) {
        if (review == null || review.review() == null) return 3;
        FacebookReview r = review.review();
        if (r.rating() >= 1 && r.rating() <= 5) {
            return r.rating();
        }
        if (r.isPositiveRecommendation()) {
            return (review.score() != null && review.score().compound() < 0.50 && review.score().compound() >= 0.05) ? 4 : 5;
        }
        if (r.isNegativeRecommendation()) {
            return (review.score() != null && review.score().compound() > -0.50 && review.score().compound() <= -0.05) ? 2 : 1;
        }
        if (review.score() != null) {
            return mapScoreToStars(review.score());
        }
        return 3;
    }
}
