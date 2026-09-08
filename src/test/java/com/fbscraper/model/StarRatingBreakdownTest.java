package com.fbscraper.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StarRatingBreakdownTest {

    @Test
    void shouldReturnEmptyWhenNoFeedback() {
        StarRatingBreakdown breakdown = StarRatingBreakdown.compute(List.of(), List.of());
        assertThat(breakdown.hasRatings()).isFalse();
        assertThat(breakdown.averageRating()).isEqualTo(0.0);
        assertThat(breakdown.totalCount()).isEqualTo(0);
        assertThat(breakdown.formattedStars()).isEqualTo("☆☆☆☆☆");
    }

    @Test
    void shouldCalculateFiveStarsForStrongPositiveFeedback() {
        FacebookComment comment1 = new FacebookComment("c1", "Love this service!", Instant.now());
        SentimentScore score1 = new SentimentScore(0.85, 0.6, 0.4, 0.0, SentimentLevel.POSITIVE);
        AnalyzedComment ac1 = new AnalyzedComment(comment1, "p1", "Snippet", score1);

        FacebookReview rev1 = new FacebookReview(Instant.now(), "positive", "Superb!", 5, true);
        AnalyzedReview ar1 = new AnalyzedReview(rev1, score1);

        StarRatingBreakdown breakdown = StarRatingBreakdown.compute(List.of(ac1), List.of(ar1));

        assertThat(breakdown.hasRatings()).isTrue();
        assertThat(breakdown.totalCount()).isEqualTo(2);
        assertThat(breakdown.fiveStarCount()).isEqualTo(2);
        assertThat(breakdown.averageRating()).isEqualTo(5.0);
        assertThat(breakdown.fiveStarPercent()).isEqualTo(100.0);
        assertThat(breakdown.formattedStars()).isEqualTo("★★★★★");
        assertThat(breakdown.totalPositive()).isEqualTo(2);
        assertThat(breakdown.totalNegative()).isEqualTo(0);
    }

    @Test
    void shouldCalculateOneStarForCriticalNegativeFeedback() {
        FacebookComment comment = new FacebookComment("c1", "Terrible scam, hate it!", Instant.now());
        SentimentScore score = new SentimentScore(-0.75, 0.0, 0.3, 0.7, SentimentLevel.CRITICAL_NEGATIVE);
        AnalyzedComment ac = new AnalyzedComment(comment, "p1", "Snippet", score);

        StarRatingBreakdown breakdown = StarRatingBreakdown.compute(List.of(ac), List.of());

        assertThat(breakdown.totalCount()).isEqualTo(1);
        assertThat(breakdown.oneStarCount()).isEqualTo(1);
        assertThat(breakdown.averageRating()).isEqualTo(1.0);
        assertThat(breakdown.oneStarPercent()).isEqualTo(100.0);
        assertThat(breakdown.formattedStars()).isEqualTo("★☆☆☆☆");
        assertThat(breakdown.totalNegative()).isEqualTo(1);
    }

    @Test
    void shouldAccuratelyMapMixedRatingsAcrossFiveTiers() {
        // 5 Star (compound >= 0.50)
        AnalyzedComment ac5 = new AnalyzedComment(
                new FacebookComment("1", "Amazing!", Instant.now()), "p", "s",
                new SentimentScore(0.70, 0.5, 0.5, 0.0, SentimentLevel.POSITIVE));

        // 4 Star (0.05 <= compound < 0.50)
        AnalyzedComment ac4 = new AnalyzedComment(
                new FacebookComment("2", "Good experience", Instant.now()), "p", "s",
                new SentimentScore(0.30, 0.3, 0.7, 0.0, SentimentLevel.POSITIVE));

        // 3 Star (-0.05 < compound < 0.05)
        AnalyzedComment ac3 = new AnalyzedComment(
                new FacebookComment("3", "What time do you open?", Instant.now()), "p", "s",
                new SentimentScore(0.00, 0.0, 1.0, 0.0, SentimentLevel.NEUTRAL));

        // 2 Star (-0.50 < compound <= -0.05)
        AnalyzedComment ac2 = new AnalyzedComment(
                new FacebookComment("4", "Slight delay in delivery", Instant.now()), "p", "s",
                new SentimentScore(-0.25, 0.0, 0.8, 0.2, SentimentLevel.WARNING_NEGATIVE));

        // 1 Star (compound <= -0.50)
        AnalyzedComment ac1 = new AnalyzedComment(
                new FacebookComment("5", "Horrible, worst app ever!", Instant.now()), "p", "s",
                new SentimentScore(-0.65, 0.0, 0.2, 0.8, SentimentLevel.CRITICAL_NEGATIVE));

        // Weighted: (5 + 4 + 3 + 2 + 1) / 5 = 15 / 5 = 3.0
        StarRatingBreakdown breakdown = StarRatingBreakdown.compute(List.of(ac5, ac4, ac3, ac2, ac1), List.of());

        assertThat(breakdown.totalCount()).isEqualTo(5);
        assertThat(breakdown.fiveStarCount()).isEqualTo(1);
        assertThat(breakdown.fourStarCount()).isEqualTo(1);
        assertThat(breakdown.threeStarCount()).isEqualTo(1);
        assertThat(breakdown.twoStarCount()).isEqualTo(1);
        assertThat(breakdown.oneStarCount()).isEqualTo(1);
        assertThat(breakdown.averageRating()).isEqualTo(3.0);
        assertThat(breakdown.formattedStars()).isEqualTo("★★★☆☆");
        assertThat(breakdown.totalPositive()).isEqualTo(2);
        assertThat(breakdown.totalNegative()).isEqualTo(2);
    }

    @Test
    void shouldHonorReviewNumericRatings() {
        FacebookReview rev4 = new FacebookReview(Instant.now(), null, "Pretty solid", 4, true);
        AnalyzedReview ar4 = new AnalyzedReview(rev4, new SentimentScore(0.2, 0.2, 0.8, 0.0, SentimentLevel.POSITIVE));

        FacebookReview rev2 = new FacebookReview(Instant.now(), null, "Could be better", 2, true);
        AnalyzedReview ar2 = new AnalyzedReview(rev2, new SentimentScore(-0.1, 0.0, 0.9, 0.1, SentimentLevel.WARNING_NEGATIVE));

        // (4 + 2) / 2 = 3.0
        StarRatingBreakdown breakdown = StarRatingBreakdown.compute(List.of(), List.of(ar4, ar2));

        assertThat(breakdown.totalCount()).isEqualTo(2);
        assertThat(breakdown.fourStarCount()).isEqualTo(1);
        assertThat(breakdown.twoStarCount()).isEqualTo(1);
        assertThat(breakdown.averageRating()).isEqualTo(3.0);
    }
}
