package com.fbscraper.web;

import com.fbscraper.client.FacebookClient;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookConversation;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.FacebookReview;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.ReactionSummary;
import com.fbscraper.sentiment.VaderAnalyzer;
import com.fbscraper.service.DataExportService;
import com.fbscraper.service.SentimentSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalWebServerTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldServeDashboardAndRunSync(@TempDir Path tempDir) throws Exception {
        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05);
        Instant now = Instant.parse("2026-09-08T08:00:00Z");
        FacebookComment comment = new FacebookComment(
                "c1", "Awful support", now, new ReactionSummary(2, 1, 0, 0, 0, 0, 0, 1)
        );
        FacebookPost post = new FacebookPost(
                "p1", "Support", now, List.of(comment), new ReactionSummary(5, 2, 0, 1, 0, 0, 0, 2)
        );
        FacebookClient facebookClient = new FacebookClient(config, request -> {
            throw new AssertionError("Unexpected HTTP call");
        }) {
            @Override public List<FacebookPost> fetchPageFeed() { return List.of(post); }
            @Override public PageRatingSummary fetchPageRatingSummary() { return new PageRatingSummary(4.2, 10); }
            @Override public List<FacebookReview> fetchPageReviews() {
                return List.of(new FacebookReview(now, "positive", "Good", 5, true));
            }
            @Override public List<FacebookConversation> fetchPageConversations() {
                return List.of();
            }
        };
        SentimentSyncService syncService = new SentimentSyncService(
                config, facebookClient, VaderAnalyzer.createDefault(), new DataExportService(tempDir)
        );

        try (LocalWebServer server = new LocalWebServer(0, syncService)) {
            server.start();
            String baseUrl = "http://127.0.0.1:" + server.port();

            HttpResponse<String> dashboard = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> sync = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/sync"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertThat(dashboard.statusCode()).isEqualTo(200);
            assertThat(dashboard.body()).contains("Sync now", "Customer sentiment", "Messages", "Messenger inbox");
            assertThat(sync.statusCode()).isEqualTo(200);
            assertThat(sync.body()).contains(
                    "\"totalPosts\":1",
                    "\"totalComments\":1",
                    "\"totalReactions\":5",
                    "\"totalCommentReactions\":2",
                    "\"commentReactionTotals\"",
                    "\"totalReviews\":1",
                    "\"care\":1",
                    "\"angry\":2",
                    "\"messageSummary\"",
                    "\"conversations\""
            );
        }
    }
}
