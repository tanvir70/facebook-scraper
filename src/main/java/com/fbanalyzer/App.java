package com.fbanalyzer;

import com.fbanalyzer.client.FacebookClient;
import com.fbanalyzer.config.AppConfig;
import com.fbanalyzer.model.AnalyzedComment;
import com.fbanalyzer.model.FacebookComment;
import com.fbanalyzer.model.FacebookPost;
import com.fbanalyzer.model.SentimentScore;
import com.fbanalyzer.report.HtmlDashboardGenerator;
import com.fbanalyzer.sentiment.VaderAnalyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class App {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   Facebook Page Negative Sentiment Analyzer     ");
        System.out.println("==================================================");

        // 1. Load Configuration
        AppConfig config = AppConfig.load();
        System.out.println("Mode: " + (config.offlineMode() ? "OFFLINE (Mock Feed)" : "LIVE Facebook Graph API"));
        System.out.println("Negative Alert Threshold: compound <= " + config.negativeThreshold());
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

        System.out.println("==================================================");
        System.out.println("Dashboard Ready: " + outputPath.toAbsolutePath());
        System.out.println("Open it in your browser (e.g. google-chrome " + outputPath.toAbsolutePath() + ")");
        System.out.println("==================================================");
    }
}
