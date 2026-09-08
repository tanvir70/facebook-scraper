package com.fbscraper.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.FacebookReview;
import com.fbscraper.model.PageRatingSummary;

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


public class FacebookClient {

    public record FeedPage(List<FacebookPost> posts, String nextUrl) {
    }

    public record ReviewPage(List<FacebookReview> reviews, String nextUrl) {
    }

    @FunctionalInterface
    public interface HttpSender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private final AppConfig config;
    private final HttpSender httpSender;
    private final ObjectMapper objectMapper;

    public FacebookClient(AppConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    public FacebookClient(AppConfig config, HttpClient httpClient) {
        this(config, req -> httpClient.send(req, HttpResponse.BodyHandlers.ofString()));
    }

    public FacebookClient(AppConfig config, HttpSender httpSender) {
        this.config = config;
        this.httpSender = httpSender;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }


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
                    if (currentUrl != null && !currentUrl.isBlank()) {
                        if (!currentUrl.contains("access_token=") && config.accessToken() != null && !config.accessToken().isBlank()) {
                            currentUrl += (currentUrl.contains("?") ? "&" : "?") + "access_token=" + URLEncoder.encode(config.accessToken(), StandardCharsets.UTF_8);
                        }
                    } else {
                        System.out.println("[FacebookClient] No next page cursor found. Feed scrape complete.");
                    }
                } else {
                    throw handleApiError(response.statusCode(), response.body());
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


    public String buildFeedUrl() {
        String fieldsParam = URLEncoder.encode(
                String.format("id,message,created_time,permalink_url,comments.limit(%d){id,message,created_time}", config.commentLimit()),
                StandardCharsets.UTF_8
        );
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "https://graph.facebook.com/%s/%s/feed?fields=%s&limit=%d",
                config.apiVersion(), config.pageId(), fieldsParam, config.feedLimit()
        ));
        if (config.accessToken() != null && !config.accessToken().isBlank()) {
            sb.append("&access_token=").append(URLEncoder.encode(config.accessToken(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }


    public PageRatingSummary fetchPageRatingSummary() {
        if (config.pageId() == null || config.pageId().isBlank() ||
                config.accessToken() == null || config.accessToken().isBlank()) {
            return PageRatingSummary.EMPTY;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "https://graph.facebook.com/%s/%s?fields=overall_star_rating,rating_count",
                config.apiVersion(), config.pageId()
        ));
        if (config.accessToken() != null && !config.accessToken().isBlank()) {
            sb.append("&access_token=").append(URLEncoder.encode(config.accessToken(), StandardCharsets.UTF_8));
        }
        String url = sb.toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + config.accessToken())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpSender.send(request);
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                double overall = root.path("overall_star_rating").asDouble(0.0);
                int count = root.path("rating_count").asInt(0);
                return new PageRatingSummary(overall, count);
            } else {
                return PageRatingSummary.EMPTY;
            }
        } catch (Exception e) {
            System.err.println("[FacebookClient] Note: Could not fetch page rating summary (" + e.getMessage() + ")");
            return PageRatingSummary.EMPTY;
        }
    }


    public List<FacebookReview> fetchPageReviews() {
        if (config.pageId() == null || config.pageId().isBlank() ||
                config.accessToken() == null || config.accessToken().isBlank()) {
            return List.of();
        }

        List<FacebookReview> allReviews = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "https://graph.facebook.com/%s/%s/ratings?fields=created_time,recommendation_type,review_text,rating,has_review&limit=%d",
                config.apiVersion(), config.pageId(), config.feedLimit()
        ));
        if (config.accessToken() != null && !config.accessToken().isBlank()) {
            sb.append("&access_token=").append(URLEncoder.encode(config.accessToken(), StandardCharsets.UTF_8));
        }
        String currentUrl = sb.toString();
        int pageCount = 0;

        System.out.println("[FacebookClient] Fetching Page Ratings and Reviews from Meta Graph API...");

        while (currentUrl != null && !currentUrl.isBlank()) {
            pageCount++;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(currentUrl))
                    .header("Authorization", "Bearer " + config.accessToken())
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpSender.send(request);
                if (response.statusCode() == 200) {
                    ReviewPage reviewPage = parseReviewPage(response.body());
                    if (reviewPage.reviews().isEmpty()) {
                        break;
                    }

                    allReviews.addAll(reviewPage.reviews());
                    System.out.printf("[FacebookClient] Reviews page %d retrieved %d review(s) (Total so far: %d)%n",
                            pageCount, reviewPage.reviews().size(), allReviews.size());

                    if (config.maxPages() > 0 && pageCount >= config.maxPages()) {
                        break;
                    }

                    currentUrl = reviewPage.nextUrl();
                    if (currentUrl != null && !currentUrl.isBlank()) {
                        if (!currentUrl.contains("access_token=") && config.accessToken() != null && !config.accessToken().isBlank()) {
                            currentUrl += (currentUrl.contains("?") ? "&" : "?") + "access_token=" + URLEncoder.encode(config.accessToken(), StandardCharsets.UTF_8);
                        }
                    }
                } else {
                    // Reviews might be disabled or restricted in the Facebook Page settings
                    break;
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                System.err.println("[FacebookClient] Could not fetch page reviews: " + e.getMessage());
                break;
            }
        }

        return allReviews;
    }

    private RuntimeException handleApiError(int statusCode, String responseBody) {
        String message = "";
        int code = -1;
        int subcode = -1;

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errNode = root.path("error");
            if (!errNode.isMissingNode()) {
                message = errNode.path("message").asText("");
                code = errNode.path("code").asInt(-1);
                subcode = errNode.path("error_subcode").asInt(-1);
            }
        } catch (Exception ignored) {
            // fallback to raw body
        }

        boolean isAuthError = statusCode == 401 || code == 190 || subcode == 463 || subcode == 467 ||
                message.toLowerCase().contains("access token") ||
                message.toLowerCase().contains("session has expired");

        if (isAuthError) {
            System.err.println("\n================================================================================");
            System.err.println("[FacebookClient] AUTHENTICATION ERROR: Facebook Access Token Expired or Invalid!");
            System.err.println("--------------------------------------------------------------------------------");
            System.err.printf("HTTP Status   : %d%n", statusCode);
            if (code > 0) {
                System.err.printf("OAuth Code    : %d (Subcode: %d)%n", code, subcode);
            }
            System.err.printf("Details       : %s%n", message.isBlank() ? responseBody : message);
            System.err.println("\nACTION REQUIRED:");
            System.err.println("1. Open Meta Graph API Explorer: https://developers.facebook.com/tools/explorer/");
            System.err.println("2. Select your Facebook Page and ensure permissions:");
            System.err.println("   - pages_read_engagement");
            System.err.println("   - pages_read_user_content");
            System.err.println("3. Generate a new Page Access Token.");
            System.err.println("4. Update 'fb.access.token' in 'config.properties'.");
            System.err.println("================================================================================\n");

            return new IllegalStateException("Facebook Access Token is expired or invalid. Update 'fb.access.token' in config.properties. (" + (message.isBlank() ? responseBody : message) + ")");
        }

        return new RuntimeException("Facebook API error [HTTP " + statusCode + "]: " + responseBody);
    }


    public ReviewPage parseReviewPage(String json) {
        List<FacebookReview> reviews = new ArrayList<>();
        String nextUrl = null;

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.path("data");
            if (dataArray.isArray()) {
                for (JsonNode item : dataArray) {
                    Instant createdTime = parseInstant(item.path("created_time").asText(""));
                    String recType = item.hasNonNull("recommendation_type") ? item.path("recommendation_type").asText() : null;
                    String reviewText = item.path("review_text").asText("");
                    int rating = item.path("rating").asInt(0);
                    boolean hasReview = item.path("has_review").asBoolean(!reviewText.isBlank());

                    reviews.add(new FacebookReview(createdTime, recType, reviewText, rating, hasReview));
                }
            }

            JsonNode nextNode = root.path("paging").path("next");
            if (!nextNode.isMissingNode() && !nextNode.isNull() && !nextNode.asText().isBlank()) {
                nextUrl = nextNode.asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Facebook reviews JSON", e);
        }

        return new ReviewPage(reviews, nextUrl);
    }
}
