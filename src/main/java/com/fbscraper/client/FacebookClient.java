package com.fbscraper.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookPost;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for fetching and parsing Facebook Page feed posts and nested comments via Meta Graph API.
 * <p>
 * Supports cursor-based pagination (following {@code paging.next}) and configurable post/comment limits.
 */
public class FacebookClient {

    /**
     * Represents a single page of feed posts and an optional cursor URL for the subsequent page.
     *
     * @param posts   the list of posts parsed on this page
     * @param nextUrl the URL to retrieve the next page of posts, or {@code null} if no further pages exist
     */
    public record FeedPage(List<FacebookPost> posts, String nextUrl) {}

    /**
     * Functional interface for sending an HTTP request and returning an HTTP response.
     */
    @FunctionalInterface
    public interface HttpSender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private final AppConfig config;
    private final HttpSender httpSender;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a {@code FacebookClient} using the standard {@link HttpClient}.
     *
     * @param config the application configuration
     */
    public FacebookClient(AppConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    /**
     * Dependency-injection constructor using an {@link HttpClient} instance.
     *
     * @param config     the application configuration
     * @param httpClient the {@link HttpClient} instance for API calls
     */
    public FacebookClient(AppConfig config, HttpClient httpClient) {
        this(config, req -> httpClient.send(req, HttpResponse.BodyHandlers.ofString()));
    }

    /**
     * Dependency-injection constructor using a custom {@link HttpSender}.
     *
     * @param config     the application configuration
     * @param httpSender the {@link HttpSender} for executing HTTP requests
     */
    public FacebookClient(AppConfig config, HttpSender httpSender) {
        this.config = config;
        this.httpSender = httpSender;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Fetches the page feed (posts and nested comments) with automatic cursor-based pagination.
     *
     * @return an aggregated list of all parsed {@link FacebookPost} instances across pages
     * @throws IllegalStateException if credentials are not configured
     * @throws RuntimeException      if the API call or JSON parsing fails
     */
    public List<FacebookPost> fetchPageFeed() {
        if (config.pageId() == null || config.pageId().isBlank() ||
            config.accessToken() == null || config.accessToken().isBlank()) {
            throw new IllegalStateException("Missing required fb.page.id or fb.access.token in config.properties");
        }

        List<FacebookPost> allPosts = new ArrayList<>();
        String currentUrl = buildFeedUrl();
        int pageCount = 0;

        System.out.println("[FacebookClient] Connecting to Facebook Graph API: " + config.apiVersion() + "/" + config.pageId());
        System.out.printf("[FacebookClient] Posts/Page: %d | Comments/Post: %d | Max Pages: %s%n",
                config.feedLimit(), config.commentLimit(),
                config.maxPages() <= 0 ? "unlimited" : String.valueOf(config.maxPages()));

        while (currentUrl != null && !currentUrl.isBlank()) {
            pageCount++;
            System.out.printf("[FacebookClient] Fetching feed page %d...%n", pageCount);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(currentUrl))
                    .header("Authorization", "Bearer " + config.accessToken())
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpSender.send(request);
                if (response.statusCode() == 200) {
                    FeedPage feedPage = parseFeedPage(response.body());
                    if (feedPage.posts().isEmpty()) {
                        System.out.printf("[FacebookClient] Page %d contained no posts. Reached end of feed.%n", pageCount);
                        break;
                    }

                    allPosts.addAll(feedPage.posts());
                    System.out.printf("[FacebookClient] Page %d retrieved %d post(s) (Total accumulated: %d posts)%n",
                            pageCount, feedPage.posts().size(), allPosts.size());

                    if (config.maxPages() > 0 && pageCount >= config.maxPages()) {
                        System.out.printf("[FacebookClient] Reached max page limit (%d). Stopping pagination.%n", config.maxPages());
                        break;
                    }

                    currentUrl = feedPage.nextUrl();
                    if (currentUrl == null || currentUrl.isBlank()) {
                        System.out.println("[FacebookClient] No next page cursor found. Feed scrape complete.");
                    }
                } else {
                    throw new RuntimeException("Facebook API error [HTTP " + response.statusCode() + "]: " + response.body());
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new RuntimeException("Failed to call Facebook Graph API: " + e.getMessage(), e);
            }
        }

        return allPosts;
    }

    /**
     * Parses a Facebook Graph API feed JSON response into a {@link FeedPage}
     * containing domain objects and the next cursor URL if present.
     *
     * @param json raw JSON string from Facebook Graph API
     * @return a {@link FeedPage} record containing parsed posts and the next pagination URL
     */
    public FeedPage parseFeedPage(String json) {
        List<FacebookPost> posts = new ArrayList<>();
        String nextUrl = null;

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

            JsonNode nextNode = root.path("paging").path("next");
            if (!nextNode.isMissingNode() && !nextNode.isNull() && !nextNode.asText().isBlank()) {
                nextUrl = nextNode.asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Facebook feed JSON", e);
        }

        return new FeedPage(posts, nextUrl);
    }

    /**
     * Parses Facebook Graph API JSON feed response into a list of {@link FacebookPost} domain objects.
     *
     * @param json raw JSON string from Facebook Graph API
     * @return a list of parsed {@link FacebookPost} instances
     */
    public List<FacebookPost> parseFeedJson(String json) {
        return parseFeedPage(json).posts();
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

    /**
     * Constructs and URL-encodes the initial Graph API feed endpoint URI with configured limits.
     *
     * @return the fully qualified and properly encoded URL string
     */
    public String buildFeedUrl() {
        String fieldsParam = URLEncoder.encode(
                String.format("id,message,created_time,permalink_url,comments.limit(%d){id,message,created_time}", config.commentLimit()),
                StandardCharsets.UTF_8
        );
        return String.format(
                "https://graph.facebook.com/%s/%s/feed?fields=%s&limit=%d",
                config.apiVersion(), config.pageId(), fieldsParam, config.feedLimit()
        );
    }
}
