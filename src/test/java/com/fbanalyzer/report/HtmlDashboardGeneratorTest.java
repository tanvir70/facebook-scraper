package com.fbanalyzer.report;

import com.fbanalyzer.model.*;
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

        assertThat(html).contains("Facebook Page Sentiment Analysis Dashboard");
        assertThat(html).contains("Terrible app, crashes constantly!");
        assertThat(html).contains("CRITICAL");
        assertThat(html).contains("chart.js");
        assertThat(html).contains("Total Comments");
    }
}
