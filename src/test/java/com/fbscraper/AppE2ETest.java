package com.fbscraper;

import com.fbscraper.client.FacebookClient;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.AnalyzedComment;
import com.fbscraper.model.AnalyzedReview;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.FacebookReview;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.ReactionSummary;
import com.fbscraper.model.SentimentScore;
import com.fbscraper.model.SyncResult;
import com.fbscraper.report.HtmlDashboardGenerator;
import com.fbscraper.sentiment.VaderAnalyzer;
import com.fbscraper.service.SentimentSyncService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppE2ETest {

    @Test
    void shouldRunTheDashboardSyncPipeline() {
        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05);
        Instant now = Instant.parse("2026-09-08T08:00:00Z");
        FacebookComment comment = new FacebookComment(
                "c1",
                "This service is horrible",
                now,
                new ReactionSummary(3, 1, 0, 1, 0, 0, 0, 1)
        );
        FacebookPost post = new FacebookPost(
                "p1",
                "Support update",
                now,
                List.of(comment),
                new ReactionSummary(10, 4, 1, 1, 1, 0, 1, 2)
        );
        FacebookReview review = new FacebookReview(now, "negative", "Very poor support", 2, true);

        FacebookClient client = new FacebookClient(config, request -> {
            throw new AssertionError("The test client must not make HTTP calls");
        }) {
            @Override
            public List<FacebookPost> fetchPageFeed() {
                return List.of(post);
            }

            @Override
            public PageRatingSummary fetchPageRatingSummary() {
                return new PageRatingSummary(3.8, 42);
            }

            @Override
            public List<FacebookReview> fetchPageReviews() {
                return List.of(review);
            }
        };

        SyncResult result = new SentimentSyncService(
                config,
                client,
                VaderAnalyzer.createDefault()
        ).sync();

        assertThat(result.totalPosts()).isEqualTo(1);
        assertThat(result.totalComments()).isEqualTo(1);
        assertThat(result.totalReactions()).isEqualTo(10);
        assertThat(result.reactionTotals().care()).isEqualTo(1);
        assertThat(result.totalCommentReactions()).isEqualTo(3);
        assertThat(result.comments().get(0).reactions().care()).isEqualTo(1);
        assertThat(result.totalReviews()).isEqualTo(1);
        assertThat(result.negativeReviews()).isEqualTo(1);
        assertThat(result.pageRating().overallStarRating()).isEqualTo(3.8);
    }

    @Test
    void shouldGenerateTheLegacyHtmlReportWithReviews() throws Exception {
        Instant now = Instant.now();
        FacebookComment comment = new FacebookComment("c1", "Great service", now);
        FacebookPost post = new FacebookPost("p1", "Update", now, List.of(comment));
        SentimentScore score = VaderAnalyzer.createDefault().analyze(comment.message());
        AnalyzedComment analyzedComment = new AnalyzedComment(comment, post.id(), post.message(), score);
        FacebookReview review = new FacebookReview(now, "positive", "Recommended", 5, true);
        AnalyzedReview analyzedReview = new AnalyzedReview(review, VaderAnalyzer.createDefault().analyze(review.reviewText()));
        Path dashboard = Files.createTempFile("facebook-dashboard-", ".html");

        new HtmlDashboardGenerator().generateReport(
                List.of(post),
                List.of(analyzedComment),
                new PageRatingSummary(4.7, 20),
                List.of(analyzedReview),
                dashboard
        );

        assertThat(Files.readString(dashboard)).contains("Overall Page Rating", "Recommended");
        Files.deleteIfExists(dashboard);
    }
}
