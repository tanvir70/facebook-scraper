package com.fbscraper.report;

import com.fbscraper.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlDashboardGeneratorTest {

    @Test
    void shouldGenerateHtmlDashboardWithMetricsAndTable(@TempDir Path tempDir) throws IOException {
        Path reportFile = tempDir.resolve("dashboard_test.html");

        Instant now = Instant.now();
        FacebookComment badComment = new FacebookComment("c1", "Terrible app, crashes constantly!", now);
        FacebookComment goodComment = new FacebookComment("c2", "Loving the new design!", now);

        FacebookPost post = new FacebookPost("p1", "Welcome to our page", now, List.of(badComment, goodComment));

        SentimentScore badScore = new SentimentScore(-0.68, 0.0, 0.2, 0.8, SentimentLevel.CRITICAL_NEGATIVE);
        SentimentScore goodScore = new SentimentScore(0.72, 0.8, 0.2, 0.0, SentimentLevel.POSITIVE);

        AnalyzedComment analyzedBad = new AnalyzedComment(badComment, "p1", "Welcome to our page", badScore);
        AnalyzedComment analyzedGood = new AnalyzedComment(goodComment, "p1", "Welcome to our page", goodScore);

        HtmlDashboardGenerator generator = new HtmlDashboardGenerator();
        generator.generateReport(List.of(post), List.of(analyzedBad, analyzedGood), reportFile);

        assertThat(Files.exists(reportFile)).isTrue();
        String html = Files.readString(reportFile);

        assertThat(html).contains("Facebook Scraper Dashboard");
        assertThat(html).contains("Terrible app, crashes constantly!");
        assertThat(html).contains("CRITICAL");
        assertThat(html).contains("chart.js");
        assertThat(html).contains("Total Comments");
    }

    @Test
    void shouldGenerateDashboardWithPageRatingsAndReviews(@TempDir Path tempDir) throws IOException {
        Path reportFile = tempDir.resolve("dashboard_reviews_test.html");
        Instant now = Instant.now();

        PageRatingSummary summary = new PageRatingSummary(4.7, 95);

        FacebookReview rev1 = new FacebookReview(now, "positive", "Super helpful service, loved it!", 5, true);
        FacebookReview rev2 = new FacebookReview(now, "negative", "Slow delivery and poor support.", 1, true);

        AnalyzedReview ar1 = new AnalyzedReview(rev1, new SentimentScore(0.75, 0.5, 0.5, 0.0, SentimentLevel.POSITIVE));
        AnalyzedReview ar2 = new AnalyzedReview(rev2, new SentimentScore(-0.65, 0.0, 0.3, 0.7, SentimentLevel.CRITICAL_NEGATIVE));

        HtmlDashboardGenerator generator = new HtmlDashboardGenerator();
        generator.generateReport(List.of(), List.of(), summary, List.of(ar1, ar2), reportFile);

        assertThat(Files.exists(reportFile)).isTrue();
        String html = Files.readString(reportFile);

        assertThat(html).contains("Overall Page Rating");
        assertThat(html).contains("4.7");
        assertThat(html).contains("95 total ratings");
        assertThat(html).contains("Customer Reviews & Recommendations (2)");
        assertThat(html).contains("Super helpful service, loved it!");
        assertThat(html).contains("Slow delivery and poor support.");
        assertThat(html).contains("👍 Recommends");
        assertThat(html).contains("👎 Doesn't Rec.");
    }
}
