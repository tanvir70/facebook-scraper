package com.fbscraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.model.*;
import com.fbscraper.report.HtmlDashboardGenerator;
import com.fbscraper.sentiment.VaderAnalyzer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppE2ETest {

    @Test
    void shouldExecutePipelineAndGenerateOutputs() throws IOException {
        // Prepare test data
        FacebookComment c1 = new FacebookComment("c1", "This update is HORRIBLE! Absolute disaster.", Instant.now());
        FacebookComment c2 = new FacebookComment("c2", "Awesome job, absolutely love the new features!", Instant.now());
        FacebookPost post = new FacebookPost("p1", "Product Launch Post", Instant.now(), List.of(c1, c2));
        List<FacebookPost> posts = List.of(post);

        PageRatingSummary ratingSummary = new PageRatingSummary(4.5, 50);
        FacebookReview review1 = new FacebookReview(Instant.now(), "positive", "Outstanding service, highly recommended!", 5, true);
        List<FacebookReview> reviews = List.of(review1);

        // Run analyzer
        VaderAnalyzer analyzer = VaderAnalyzer.createDefault();
        List<AnalyzedComment> analyzedComments = new ArrayList<>();
        for (FacebookComment c : post.comments()) {
            SentimentScore score = analyzer.analyze(c.message());
            analyzedComments.add(new AnalyzedComment(c, post.id(), post.message(), score));
        }

        List<AnalyzedReview> analyzedReviews = new ArrayList<>();
        for (FacebookReview rev : reviews) {
            SentimentScore score = analyzer.analyze(rev.reviewText());
            analyzedReviews.add(new AnalyzedReview(rev, score));
        }

        // Generate dashboard
        Path dashboard = Path.of("output/test_dashboard.html");
        HtmlDashboardGenerator generator = new HtmlDashboardGenerator();
        generator.generateReport(posts, analyzedComments, ratingSummary, analyzedReviews, dashboard);

        // Generate comments and reviews JSON
        Path jsonPath = Path.of("output/test_comments.json");
        Path reviewsJsonPath = Path.of("output/test_reviews.json");
        Path ratingJsonPath = Path.of("output/test_rating_summary.json");
        if (jsonPath.getParent() != null) {
            Files.createDirectories(jsonPath.getParent());
        }
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), analyzedComments);
        mapper.writerWithDefaultPrettyPrinter().writeValue(reviewsJsonPath.toFile(), analyzedReviews);
        StarRatingBreakdown starRating = StarRatingBreakdown.compute(analyzedComments, analyzedReviews);
        mapper.writerWithDefaultPrettyPrinter().writeValue(ratingJsonPath.toFile(), starRating);

        // Verify outputs
        assertThat(Files.exists(dashboard)).isTrue();
        assertThat(Files.exists(jsonPath)).isTrue();
        assertThat(Files.exists(reviewsJsonPath)).isTrue();
        assertThat(Files.exists(ratingJsonPath)).isTrue();

        String html = Files.readString(dashboard);
        assertThat(html).contains("Facebook Scraper Dashboard");
        assertThat(html).contains("This update is HORRIBLE!");
        assertThat(html).contains("1 positive comments");
        assertThat(html).contains("Overall 5-Star Rating");
        assertThat(html).contains("5-Star Rating Breakdown");
        assertThat(html).contains("Meta Official Rating");
        assertThat(html).contains("4.5");
        assertThat(html).contains("Outstanding service, highly recommended!");

        String json = Files.readString(jsonPath);
        assertThat(json).contains("This update is HORRIBLE!");
        assertThat(json).contains("Awesome job");
        assertThat(json).contains("compound");

        String revJson = Files.readString(reviewsJsonPath);
        assertThat(revJson).contains("Outstanding service, highly recommended!");
        assertThat(revJson).contains("POSITIVE");

        String ratingJson = Files.readString(ratingJsonPath);
        assertThat(ratingJson).contains("averageRating");
        assertThat(ratingJson).contains("fiveStarCount");

        // Clean up test outputs
        Files.deleteIfExists(dashboard);
        Files.deleteIfExists(jsonPath);
        Files.deleteIfExists(reviewsJsonPath);
        Files.deleteIfExists(ratingJsonPath);
    }
}
