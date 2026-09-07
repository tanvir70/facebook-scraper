package com.fbscraper;

import com.fbscraper.client.FacebookClient;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.AnalyzedComment;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.SentimentScore;
import com.fbscraper.report.HtmlDashboardGenerator;
import com.fbscraper.sentiment.VaderAnalyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application entry point for the Facebook Scraper.
 * <p>
 * Orchestrates the full pipeline:
 * <ol>
 *   <li>Loads configuration from properties or environment variables ({@link AppConfig}).</li>
 *   <li>Retrieves Facebook posts and comments via Graph API or offline mock ({@link FacebookClient}).</li>
 *   <li>Runs lexicon-based sentiment analysis on all comments ({@link VaderAnalyzer}).</li>
 *   <li>Calculates negative sentiment statistics and prints a console summary.</li>
 *   <li>Generates an interactive HTML dashboard ({@link HtmlDashboardGenerator}).</li>
 * </ol>
 */
public class App {

    /**
     * Executes the scraper and sentiment analysis pipeline.
     *
     * @param args command-line arguments (not currently required)
     */
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("                 Facebook Scraper                 ");
        System.out.println("==================================================");

        // 1. Load Configuration from config.properties
        AppConfig config;
        try {
            config = AppConfig.load();
        } catch (Exception e) {
            System.err.println("[App] Error loading configuration: " + e.getMessage());
            return;
        }

        if (config.pageId().isBlank() || config.accessToken().isBlank()) {
            System.err.println("[App] Error: fb.page.id and fb.access.token must be set in config.properties.");
            System.err.println("[App] Please edit config.properties and provide your Facebook Page credentials.");
            return;
        }

        System.out.println("Target Page ID : " + config.pageId());
        System.out.println("API Version    : " + config.apiVersion());
        System.out.println("Post Limit     : " + config.feedLimit() + " per page");
        System.out.println("Comment Limit  : " + config.commentLimit() + " per post");
        System.out.println("Max Pages      : " + (config.maxPages() <= 0 ? "Unlimited" : config.maxPages()));
        System.out.println("Alert Threshold: compound <= " + config.negativeThreshold());
        System.out.println("--------------------------------------------------");

        // 2. Fetch Facebook Page Posts and Comments
        FacebookClient client = new FacebookClient(config);
        List<FacebookPost> posts = client.fetchPageFeed();

        if (posts.isEmpty()) {
            System.out.println("[App] No posts found to analyze.");
            return;
        }

        System.out.printf("[App] Retrieved %d post(s)%n", posts.size());

        // 3. Initialize Sentiment Analyzer
        System.out.println("[App] Initializing VADER Sentiment Lexicon...");
        VaderAnalyzer analyzer = VaderAnalyzer.createDefault();

        // 4. Analyze Comments
        List<AnalyzedComment> analyzedComments = new ArrayList<>();
        for (FacebookPost post : posts) {
            String snippet = post.message() != null && !post.message().isBlank()
                    ? (post.message().length() > 60 ? post.message().substring(0, 57) + "..." : post.message())
                    : "[No post text]";

            for (FacebookComment comment : post.comments()) {
                SentimentScore score = analyzer.analyze(comment.message());
                analyzedComments.add(new AnalyzedComment(comment, post.id(), snippet, score));
            }
        }

        System.out.printf("[App] Scanned %d total comment(s)%n", analyzedComments.size());

        // 5. Compute Negative Sentiment Summary
        List<AnalyzedComment> negativeComments = analyzedComments.stream()
                .filter(c -> c.score().compound() <= config.negativeThreshold())
                .sorted((a, b) -> Double.compare(a.score().compound(), b.score().compound()))
                .toList();

        System.out.println("--------------------------------------------------");
        System.out.println("                 ANALYSIS SUMMARY                 ");
        System.out.println("--------------------------------------------------");
        System.out.printf("Total Comments Scanned  : %d%n", analyzedComments.size());
        System.out.printf("Flagged Negative Feedback: %d%n", negativeComments.size());
        double negRate = analyzedComments.isEmpty() ? 0.0 : ((double) negativeComments.size() / analyzedComments.size()) * 100.0;
        System.out.printf("Negative Sentiment Rate : %.1f%%%n", negRate);

        if (!negativeComments.isEmpty()) {
            System.out.println("\nTop Flagged Negative Comments:");
            int count = 0;
            for (AnalyzedComment neg : negativeComments) {
                if (++count > 5) break;
                System.out.printf("  [%s | Score: %.2f] \"%s\"%n",
                        neg.score().level(), neg.score().compound(),
                        neg.comment().message().replace("\n", " "));
            }
        } else {
            System.out.println("\nAll clear! No negative sentiment detected.");
        }

        // 6. Generate Standalone HTML Dashboard
        Path outputPath = Path.of("output/dashboard.html");
        HtmlDashboardGenerator generator = new HtmlDashboardGenerator();
        generator.generateReport(posts, analyzedComments, outputPath);

        // 7. Save Raw Scraped Comments as JSON
        Path jsonPath = Path.of("output/comments.json");
        try {
            if (jsonPath.getParent() != null) {
                java.nio.file.Files.createDirectories(jsonPath.getParent());
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), analyzedComments);
        } catch (java.io.IOException e) {
            System.err.println("[App] Failed to save comments.json: " + e.getMessage());
        }

        System.out.println("==================================================");
        System.out.println("Dashboard Ready : " + outputPath.toAbsolutePath());
        System.out.println("JSON Data Saved : " + jsonPath.toAbsolutePath());
        System.out.println("Open dashboard  : google-chrome " + outputPath.toAbsolutePath());
        System.out.println("==================================================");
    }
}
