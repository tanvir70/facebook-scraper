package com.fbscraper.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookPost;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class FacebookClient {

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
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public List<FacebookPost> fetchPageFeed() {
        if (config.offlineMode()) {
            System.out.println("[FacebookClient] Running in OFFLINE / MOCK mode. Loading sample feed...");
            return loadSampleFeed();
        }

        if (config.pageId() == null || config.pageId().isBlank() ||
            config.accessToken() == null || config.accessToken().isBlank()) {
            System.err.println("[FacebookClient] Missing fb.page.id or fb.access.token in config. Falling back to offline mock mode.");
            return loadSampleFeed();
        }

        String url = String.format(
                "https://graph.facebook.com/%s/%s/feed?fields=id,message,created_time,comments{id,message,created_time}&limit=25",
                config.apiVersion(), config.pageId()
        );

        System.out.println("[FacebookClient] Calling Facebook Graph API: " + config.apiVersion() + "/" + config.pageId());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + config.accessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseFeedJson(response.body());
            } else {
                throw new RuntimeException("Facebook API error [HTTP " + response.statusCode() + "]: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to call Facebook Graph API: " + e.getMessage(), e);
        }
    }

    public List<FacebookPost> parseFeedJson(String json) {
        List<FacebookPost> posts = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.path("data");
            if (dataArray.isArray()) {
                for (JsonNode postNode : dataArray) {
                    String id = postNode.path("id").asText("");
                    String message = postNode.path("message").asText("");
                    Instant createdTime = parseInstant(postNode.path("created_time").asText());

                    List<FacebookComment> comments = new ArrayList<>();
                    JsonNode commentsData = postNode.path("comments").path("data");
                    if (commentsData.isArray()) {
                        for (JsonNode commentNode : commentsData) {
                            String commentId = commentNode.path("id").asText("");
                            String commentMsg = commentNode.path("message").asText("");
                            Instant commentTime = parseInstant(commentNode.path("created_time").asText());
                            comments.add(new FacebookComment(commentId, commentMsg, commentTime));
                        }
                    }

                    posts.add(new FacebookPost(id, message, createdTime, comments));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Facebook feed JSON", e);
        }
        return posts;
    }

    private List<FacebookPost> loadSampleFeed() {
        try {
            if (Files.exists(sampleDataPath)) {
                String json = Files.readString(sampleDataPath);
                return parseFeedJson(json);
            }
            // Classpath fallback
            try (var in = getClass().getClassLoader().getResourceAsStream("sample_feed.json")) {
                if (in != null) {
                    String json = new String(in.readAllBytes());
                    return parseFeedJson(json);
                }
            }
            throw new IllegalStateException("Sample feed file not found at " + sampleDataPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Error reading sample feed: " + e.getMessage(), e);
        }
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(text);
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }
}
