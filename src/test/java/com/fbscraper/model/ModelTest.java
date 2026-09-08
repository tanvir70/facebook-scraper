package com.fbscraper.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void shouldCreateAndVerifySentimentScore() {
        SentimentScore score = new SentimentScore(-0.65, 0.05, 0.20, 0.75, SentimentLevel.CRITICAL_NEGATIVE);

        assertThat(score.compound()).isEqualTo(-0.65);
        assertThat(score.isNegative()).isTrue();
        assertThat(score.level()).isEqualTo(SentimentLevel.CRITICAL_NEGATIVE);
        assertThat(score.starRating()).isEqualTo(1);

        assertThat(new SentimentScore(0.85, 0.5, 0.5, 0.0, SentimentLevel.POSITIVE).starRating()).isEqualTo(5);
        assertThat(new SentimentScore(0.35, 0.3, 0.7, 0.0, SentimentLevel.POSITIVE).starRating()).isEqualTo(4);
        assertThat(new SentimentScore(0.00, 0.0, 1.0, 0.0, SentimentLevel.NEUTRAL).starRating()).isEqualTo(3);
        assertThat(new SentimentScore(-0.20, 0.0, 0.8, 0.2, SentimentLevel.WARNING_NEGATIVE).starRating()).isEqualTo(2);
    }

    @Test
    void shouldCreateFacebookPostWithComments() {
        Instant now = Instant.now();
        FacebookComment comment = new FacebookComment("c1", "Terrible service!", now);
        FacebookPost post = new FacebookPost("p1", "Check our new product", now, List.of(comment));

        assertThat(post.id()).isEqualTo("p1");
        assertThat(post.comments()).hasSize(1);
        assertThat(post.comments().get(0).message()).isEqualTo("Terrible service!");
    }

    @Test
    void shouldHandleNullCommentsGracefullyInPost() {
        FacebookPost post = new FacebookPost("p2", "Post without comments", Instant.now(), null);
        assertThat(post.comments()).isNotNull().isEmpty();
    }

    @Test
    void shouldCreateAndVerifyPageRatingSummary() {
        PageRatingSummary summary = new PageRatingSummary(4.6, 120);
        assertThat(summary.overallStarRating()).isEqualTo(4.6);
        assertThat(summary.ratingCount()).isEqualTo(120);
        assertThat(summary.hasRatings()).isTrue();

        assertThat(PageRatingSummary.EMPTY.hasRatings()).isFalse();
    }

    @Test
    void shouldCreateAndVerifyFacebookReviewAndAnalyzedReview() {
        Instant now = Instant.now();
        FacebookReview review = new FacebookReview(now, "positive", "Great staff!", 5, true);
        assertThat(review.isPositiveRecommendation()).isTrue();
        assertThat(review.isNegativeRecommendation()).isFalse();
        assertThat(review.reviewText()).isEqualTo("Great staff!");
        assertThat(review.rating()).isEqualTo(5);

        SentimentScore score = new SentimentScore(0.62, 0.4, 0.6, 0.0, SentimentLevel.POSITIVE);
        AnalyzedReview analyzedReview = new AnalyzedReview(review, score);
        assertThat(analyzedReview.score().compound()).isEqualTo(0.62);
        assertThat(analyzedReview.review().reviewText()).isEqualTo("Great staff!");
    }
}
