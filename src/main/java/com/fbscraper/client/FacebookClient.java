package com.fbscraper.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.ReactionSummary;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for fetching and parsing Facebook Page posts, comments, and reaction counts.
 * Supports live Meta Graph API calls and offline mock execution.
 */
public class FacebookClient {

    private static final DateTimeFormatter FACEBOOK_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final AppConfig config;
    private final Path sampleDataPath;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FacebookClient(AppConfig config) {
        this(config, Path.of("data/sample_feed.json"), HttpClient.newHttpClient());
    }

    public FacebookClient(AppConfig config, Path sampleDataPath) {
        this(config, sampleDataPath, HttpClient.newHttpClient());
    }

    public FacebookClient(AppConfig config, Path sampleDataPath, HttpClient httpClient) {
        this.config = config;
        this.sampleDataPath = sampleDataPath;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Fetches the Page feed with nested comments and aggregated reactions.
     */
    public List<FacebookPost> fetchPageFeed() {
        if (config.offlineMode()) {
            System.out.println("[FacebookClient] Running in OFFLINE / MOCK mode. Loading sample feed...");
            return loadSampleFeed();
        }

        validateLiveCredentials();
        System.out.println("[FacebookClient] Fetching Page posts, comments, and reactions");
        return parseFeedJson(executeGet(buildFeedUrl()));
    }

    /**
     * Parses a Graph API Page feed response into domain objects.
     */
    public List<FacebookPost> parseFeedJson(String json) {
        List<FacebookPost> posts = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray()) {
                return posts;
            }

            for (JsonNode postNode : dataArray) {
                String id = postNode.path("id").asText("");
                String message = postNode.path("message").asText("");
                Instant createdTime = parseInstant(postNode.path("created_time").asText());

                List<FacebookComment> comments = new ArrayList<>();
                JsonNode commentsData = postNode.path("comments").path("data");
                if (commentsData.isArray()) {
                    for (JsonNode commentNode : commentsData) {
                        comments.add(new FacebookComment(
                                commentNode.path("id").asText(""),
                                commentNode.path("message").asText(""),
                                parseInstant(commentNode.path("created_time").asText())
                        ));
                    }
                }

                ReactionSummary reactions = new ReactionSummary(
                        reactionCount(postNode, "reactions"),
                        reactionCount(postNode, "like"),
                        reactionCount(postNode, "love"),
                        reactionCount(postNode, "care"),
                        reactionCount(postNode, "haha"),
                        reactionCount(postNode, "wow"),
                        reactionCount(postNode, "sad"),
                        reactionCount(postNode, "angry")
                );

                posts.add(new FacebookPost(id, message, createdTime, comments, reactions));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Facebook feed JSON", e);
        }
        return posts;
    }

    private List<FacebookPost> loadSampleFeed() {
        try {
            if (Files.exists(sampleDataPath)) {
                return parseFeedJson(Files.readString(sampleDataPath));
            }
            try (var in = getClass().getClassLoader().getResourceAsStream("sample_feed.json")) {
                if (in != null) {
                    return parseFeedJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            throw new IllegalStateException("Sample feed file not found at " + sampleDataPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Error reading sample feed: " + e.getMessage(), e);
        }
    }

    private void validateLiveCredentials() {
        if (config.pageId() == null || config.pageId().isBlank()
                || config.accessToken() == null || config.accessToken().isBlank()) {
            throw new IllegalStateException("Live mode requires fb.page.id and a Facebook Page access token");
        }
    }

    private String executeGet(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + config.accessToken())
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new RuntimeException(
                    "Facebook API error [HTTP " + response.statusCode() + "]: " + response.body()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Facebook Graph API request was interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Facebook Graph API: " + e.getMessage(), e);
        }
    }

    private int reactionCount(JsonNode postNode, String field) {
        return postNode.path(field).path("summary").path("total_count").asInt(0);
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(text, FACEBOOK_TIME_FORMATTER).toInstant();
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Unsupported Facebook timestamp: " + text, ex);
            }
        }
    }

    /**
     * Constructs and URL-encodes the Graph API feed endpoint.
     */
    public String buildFeedUrl() {
        String fieldsParam = URLEncoder.encode(
                "id,message,created_time,"
                        + "reactions.limit(0).summary(total_count),"
                        + "reactions.type(LIKE).limit(0).summary(total_count).as(like),"
                        + "reactions.type(LOVE).limit(0).summary(total_count).as(love),"
                        + "reactions.type(CARE).limit(0).summary(total_count).as(care),"
                        + "reactions.type(HAHA).limit(0).summary(total_count).as(haha),"
                        + "reactions.type(WOW).limit(0).summary(total_count).as(wow),"
                        + "reactions.type(SAD).limit(0).summary(total_count).as(sad),"
                        + "reactions.type(ANGRY).limit(0).summary(total_count).as(angry),"
                        + "comments{id,message,created_time}",
                StandardCharsets.UTF_8
        );
        return String.format(
                "https://graph.facebook.com/%s/%s/feed?fields=%s&limit=25",
                config.apiVersion(), config.pageId(), fieldsParam
        );
    }
}
