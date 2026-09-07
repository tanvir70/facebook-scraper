package com.fbscraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.model.AnalyzedComment;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.SentimentScore;
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

        // Run analyzer
        VaderAnalyzer analyzer = VaderAnalyzer.createDefault();
        List<AnalyzedComment> analyzedComments = new ArrayList<>();
        for (FacebookComment c : post.comments()) {
            SentimentScore score = analyzer.analyze(c.message());
            analyzedComments.add(new AnalyzedComment(c, post.id(), post.message(), score));
        }

        // Generate dashboard
        Path dashboard = Path.of("output/test_dashboard.html");
        HtmlDashboardGenerator generator = new HtmlDashboardGenerator();
        generator.generateReport(posts, analyzedComments, dashboard);

        // Generate comments JSON
        Path jsonPath = Path.of("output/test_comments.json");
        if (jsonPath.getParent() != null) {
            Files.createDirectories(jsonPath.getParent());
        }
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), analyzedComments);

        // Verify outputs
        assertThat(Files.exists(dashboard)).isTrue();
        assertThat(Files.exists(jsonPath)).isTrue();

        String html = Files.readString(dashboard);
        assertThat(html).contains("Facebook Scraper Dashboard");
        assertThat(html).contains("This update is HORRIBLE!");
        assertThat(html).contains("1 positive comments");

        String json = Files.readString(jsonPath);
        assertThat(json).contains("This update is HORRIBLE!");
        assertThat(json).contains("Awesome job");
        assertThat(json).contains("compound");

        // Clean up test outputs
        Files.deleteIfExists(dashboard);
        Files.deleteIfExists(jsonPath);
    }
}
