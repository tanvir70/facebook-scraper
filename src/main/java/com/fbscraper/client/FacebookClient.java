package com.fbscraper.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.FacebookAttachment;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookConversation;
import com.fbscraper.model.FacebookMessage;
import com.fbscraper.model.FacebookParticipant;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.FacebookReview;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.ReactionSummary;

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
 * Meta Graph API client for paginated Page posts, comments, reactions, ratings, reviews, and inbox messages.
 */
public class FacebookClient {

    public record FeedPage(List<FacebookPost> posts, String nextUrl) {
    }

    public record ReviewPage(List<FacebookReview> reviews, String nextUrl) {
    }

    public record ConversationPage(List<FacebookConversation> conversations, String nextUrl) {
    }

    @FunctionalInterface
    public interface HttpSender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private static final String REACTION_FIELDS =
            "reactions.limit(0).summary(total_count).as(reaction_total),"
                    + "reactions.type(LIKE).limit(0).summary(total_count).as(reaction_like),"
                    + "reactions.type(LOVE).limit(0).summary(total_count).as(reaction_love),"
                    + "reactions.type(CARE).limit(0).summary(total_count).as(reaction_care),"
                    + "reactions.type(HAHA).limit(0).summary(total_count).as(reaction_haha),"
                    + "reactions.type(WOW).limit(0).summary(total_count).as(reaction_wow),"
                    + "reactions.type(SAD).limit(0).summary(total_count).as(reaction_sad),"
                    + "reactions.type(ANGRY).limit(0).summary(total_count).as(reaction_angry)";
    private static final DateTimeFormatter FACEBOOK_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final AppConfig config;
    private final HttpSender httpSender;
    private final ObjectMapper objectMapper;

    public FacebookClient(AppConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    public FacebookClient(AppConfig config, HttpClient httpClient) {
        this(config, request -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    public FacebookClient(AppConfig config, HttpSender httpSender) {
        this.config = config;
        this.httpSender = httpSender;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public List<FacebookPost> fetchPageFeed() {
        validateCredentials();

        List<FacebookPost> allPosts = new ArrayList<>();
        String currentUrl = buildFeedUrl();
        int pageCount = 0;

        System.out.printf(
                "[FacebookClient] Fetching posts, comments, and reactions (posts/page=%d, comments/post=%d)%n",
                config.feedLimit(),
                config.commentLimit()
        );

        while (hasText(currentUrl)) {
            pageCount++;
            HttpResponse<String> response = sendGet(currentUrl, Duration.ofSeconds(20));
            if (response.statusCode() != 200) {
                throw handleApiError(response.statusCode(), response.body());
            }

            FeedPage page = parseFeedPage(response.body());
            if (page.posts().isEmpty()) {
                break;
            }

            allPosts.addAll(page.posts());
            System.out.printf(
                    "[FacebookClient] Feed page %d returned %d posts (total=%d)%n",
                    pageCount,
                    page.posts().size(),
                    allPosts.size()
            );

            if (reachedPageLimit(pageCount)) {
                break;
            }
            currentUrl = withAccessToken(page.nextUrl());
        }

        return allPosts;
    }

    public FeedPage parseFeedPage(String json) {
        List<FacebookPost> posts = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode postNode : data) {
                    List<FacebookComment> comments = new ArrayList<>();
                    JsonNode commentData = postNode.path("comments").path("data");
                    if (commentData.isArray()) {
                        for (JsonNode commentNode : commentData) {
                            comments.add(new FacebookComment(
                                    commentNode.path("id").asText(""),
                                    commentNode.path("message").asText(""),
                                    parseInstant(commentNode.path("created_time").asText("")),
                                    parseReactions(commentNode)
                            ));
                        }
                    }

                    posts.add(new FacebookPost(
                            postNode.path("id").asText(""),
                            postNode.path("message").asText(""),
                            parseInstant(postNode.path("created_time").asText("")),
                            comments,
                            parseReactions(postNode)
                    ));
                }
            }
            return new FeedPage(posts, pagingNext(root));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Facebook feed JSON", e);
        }
    }


    public String buildFeedUrl() {
        String fields = String.format(
                "id,message,created_time,permalink_url,%s,comments.limit(%d){id,message,created_time,%s}",
                REACTION_FIELDS,
                config.commentLimit(),
                REACTION_FIELDS
        );
        String url = String.format(
                "https://graph.facebook.com/%s/%s/feed?fields=%s&limit=%d",
                config.apiVersion(),
                config.pageId(),
                URLEncoder.encode(fields, StandardCharsets.UTF_8),
                config.feedLimit()
        );
        return withAccessToken(url);
    }

    public PageRatingSummary fetchPageRatingSummary() {
        if (!credentialsPresent()) {
            return PageRatingSummary.EMPTY;
        }

        String url = withAccessToken(String.format(
                "https://graph.facebook.com/%s/%s?fields=overall_star_rating,rating_count",
                config.apiVersion(),
                config.pageId()
        ));

        try {
            HttpResponse<String> response = sendGet(url, Duration.ofSeconds(15));
            if (response.statusCode() != 200) {
                return PageRatingSummary.EMPTY;
            }
            JsonNode root = objectMapper.readTree(response.body());
            return new PageRatingSummary(
                    root.path("overall_star_rating").asDouble(0.0),
                    root.path("rating_count").asInt(0)
            );
        } catch (Exception e) {
            System.err.println("[FacebookClient] Could not fetch Page rating summary: " + e.getMessage());
            return PageRatingSummary.EMPTY;
        }
    }

    public List<FacebookReview> fetchPageReviews() {
        if (!credentialsPresent()) {
            return List.of();
        }

        List<FacebookReview> allReviews = new ArrayList<>();
        String currentUrl = withAccessToken(String.format(
                "https://graph.facebook.com/%s/%s/ratings?fields=created_time,recommendation_type,review_text,rating,has_review&limit=%d",
                config.apiVersion(),
                config.pageId(),
                config.feedLimit()
        ));
        int pageCount = 0;

        while (hasText(currentUrl)) {
            pageCount++;
            try {
                HttpResponse<String> response = sendGet(currentUrl, Duration.ofSeconds(20));
                if (response.statusCode() != 200) {
                    break;
                }

                ReviewPage page = parseReviewPage(response.body());
                if (page.reviews().isEmpty()) {
                    break;
                }
                allReviews.addAll(page.reviews());

                if (reachedPageLimit(pageCount)) {
                    break;
                }
                currentUrl = withAccessToken(page.nextUrl());
            } catch (RuntimeException e) {
                System.err.println("[FacebookClient] Could not fetch Page reviews: " + e.getMessage());
                break;
            }
        }

        return allReviews;
    }

    public ReviewPage parseReviewPage(String json) {
        List<FacebookReview> reviews = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String reviewText = item.path("review_text").asText("");
                    reviews.add(new FacebookReview(
                            parseInstant(item.path("created_time").asText("")),
                            item.hasNonNull("recommendation_type")
                                    ? item.path("recommendation_type").asText("")
                                    : null,
                            reviewText,
                            item.path("rating").asInt(0),
                            item.path("has_review").asBoolean(!reviewText.isBlank())
                    ));
                }
            }
            return new ReviewPage(reviews, pagingNext(root));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Facebook reviews JSON", e);
        }
    }

    public List<FacebookConversation> fetchPageConversations() {
        if (!credentialsPresent()) {
            return List.of();
        }

        List<FacebookConversation> allConversations = new ArrayList<>();
        String currentUrl = buildConversationsUrl();
        int pageCount = 0;

        System.out.printf(
                "[FacebookClient] Fetching conversations and inbox messages (conversations/page=%d, messages/thread=%d)%n",
                config.conversationLimit(),
                config.messageLimit()
        );

        while (hasText(currentUrl)) {
            pageCount++;
            try {
                HttpResponse<String> response = sendGet(currentUrl, Duration.ofSeconds(25));
                if (response.statusCode() != 200) {
                    String body = response.body();
                    if (isMessagingPermissionError(body)) {
                        System.err.println("[FacebookClient] Page conversations inaccessible (missing pages_messaging permission on access token). Skipping inbox extraction.");
                        break;
                    }
                    System.err.println("[FacebookClient] Could not fetch Page conversations [HTTP " + response.statusCode() + "]: " + body);
                    break;
                }

                ConversationPage page = parseConversationsPage(response.body());
                if (page.conversations().isEmpty()) {
                    break;
                }
                allConversations.addAll(page.conversations());

                System.out.printf(
                        "[FacebookClient] Conversation page %d returned %d threads (total=%d)%n",
                        pageCount,
                        page.conversations().size(),
                        allConversations.size()
                );

                if (reachedPageLimit(pageCount)) {
                    break;
                }
                currentUrl = withAccessToken(page.nextUrl());
            } catch (RuntimeException e) {
                System.err.println("[FacebookClient] Could not fetch Page conversations: " + e.getMessage());
                break;
            }
        }

        return allConversations;
    }

    public String buildConversationsUrl() {
        String fields = String.format(
                "id,updated_time,participants,messages.limit(%d){id,message,created_time,from,to,attachments{id,mime_type,name,size,file_url,image_data,video_data}}",
                config.messageLimit()
        );
        String url = String.format(
                "https://graph.facebook.com/%s/%s/conversations?fields=%s&limit=%d",
                config.apiVersion(),
                config.pageId(),
                URLEncoder.encode(fields, StandardCharsets.UTF_8),
                config.conversationLimit()
        );
        return withAccessToken(url);
    }

    public ConversationPage parseConversationsPage(String json) {
        List<FacebookConversation> conversations = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode convNode : data) {
                    List<FacebookParticipant> participants = parseParticipants(convNode.path("participants").path("data"));
                    List<FacebookMessage> messages = parseMessages(convNode.path("messages").path("data"));
                    conversations.add(new FacebookConversation(
                            convNode.path("id").asText(""),
                            parseInstant(convNode.path("updated_time").asText("")),
                            participants,
                            messages
                    ));
                }
            }
            return new ConversationPage(conversations, pagingNext(root));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Facebook conversations JSON", e);
        }
    }

    private List<FacebookParticipant> parseParticipants(JsonNode data) {
        List<FacebookParticipant> list = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                list.add(new FacebookParticipant(
                        node.path("id").asText(""),
                        node.path("name").asText(""),
                        node.hasNonNull("email") ? node.path("email").asText("") : null
                ));
            }
        }
        return list;
    }

    private List<FacebookMessage> parseMessages(JsonNode data) {
        List<FacebookMessage> list = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                FacebookParticipant from = null;
                if (node.hasNonNull("from")) {
                    JsonNode fromNode = node.get("from");
                    from = new FacebookParticipant(
                            fromNode.path("id").asText(""),
                            fromNode.path("name").asText(""),
                            fromNode.hasNonNull("email") ? fromNode.path("email").asText("") : null
                    );
                }
                List<FacebookParticipant> to = parseParticipants(node.path("to").path("data"));
                List<FacebookAttachment> attachments = parseAttachments(node.path("attachments").path("data"));
                list.add(new FacebookMessage(
                        node.path("id").asText(""),
                        node.path("message").asText(""),
                        parseInstant(node.path("created_time").asText("")),
                        from,
                        to,
                        attachments
                ));
            }
        }
        return list;
    }

    private List<FacebookAttachment> parseAttachments(JsonNode data) {
        List<FacebookAttachment> list = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                String id = node.path("id").asText("");
                String mimeType = node.hasNonNull("mime_type") ? node.path("mime_type").asText() : null;
                String name = node.hasNonNull("name") ? node.path("name").asText() : null;
                Long size = node.hasNonNull("size") ? node.path("size").asLong() : null;

                String url = null;
                String previewUrl = null;

                if (node.hasNonNull("file_url")) {
                    url = node.path("file_url").asText();
                }

                if (node.hasNonNull("image_data")) {
                    JsonNode imageData = node.get("image_data");
                    if (url == null && imageData.hasNonNull("url")) {
                        url = imageData.path("url").asText();
                    }
                    if (imageData.hasNonNull("preview_url")) {
                        previewUrl = imageData.path("preview_url").asText();
                    }
                }

                if (node.hasNonNull("video_data")) {
                    JsonNode videoData = node.get("video_data");
                    if (url == null && videoData.hasNonNull("url")) {
                        url = videoData.path("url").asText();
                    }
                    if (videoData.hasNonNull("preview_url")) {
                        previewUrl = videoData.path("preview_url").asText();
                    }
                }

                list.add(new FacebookAttachment(id, mimeType, name, size, url, previewUrl));
            }
        }
        return list;
    }

    private boolean isMessagingPermissionError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String lower = responseBody.toLowerCase();
        return lower.contains("pages_messaging")
                || lower.contains("read_page_mailboxes")
                || (lower.contains("permission") && lower.contains("conversation"));
    }

    private HttpResponse<String> sendGet(String url, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + config.accessToken())
                .timeout(timeout)
                .GET()
                .build();
        try {
            return httpSender.send(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Facebook Graph API request was interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Facebook Graph API: " + e.getMessage(), e);
        }
    }

    private ReactionSummary parseReactions(JsonNode node) {
        return new ReactionSummary(
                reactionCount(node, "reaction_total", "reactions"),
                reactionCount(node, "reaction_like", "like"),
                reactionCount(node, "reaction_love", "love"),
                reactionCount(node, "reaction_care", "care"),
                reactionCount(node, "reaction_haha", "haha"),
                reactionCount(node, "reaction_wow", "wow"),
                reactionCount(node, "reaction_sad", "sad"),
                reactionCount(node, "reaction_angry", "angry")
        );
    }

    private int reactionCount(JsonNode node, String field, String legacyField) {
        JsonNode count = node.path(field).path("summary").path("total_count");
        if (!count.asText("").isBlank()) {
            return count.asInt(0);
        }
        return node.path(legacyField).path("summary").path("total_count").asInt(0);
    }

    private String pagingNext(JsonNode root) {
        JsonNode next = root.path("paging").path("next");
        return next.isMissingNode() || next.isNull() || next.asText("").isBlank()
                ? null
                : next.asText();
    }

    private Instant parseInstant(String text) {
        if (!hasText(text)) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(text, FACEBOOK_TIME_FORMATTER).toInstant();
            } catch (DateTimeParseException ignored) {
                try {
                    return Instant.parse(text);
                } catch (DateTimeParseException invalidTimestamp) {
                    return Instant.now();
                }
            }
        }
    }

    private RuntimeException handleApiError(int statusCode, String responseBody) {
        String message = "";
        int code = -1;
        int subcode = -1;

        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            message = error.path("message").asText("");
            code = error.path("code").asInt(-1);
            subcode = error.path("error_subcode").asInt(-1);
        } catch (Exception ignored) {
            // Fall back to the raw response below.
        }

        boolean authError = statusCode == 401
                || code == 190
                || subcode == 463
                || subcode == 467
                || message.toLowerCase().contains("access token")
                || message.toLowerCase().contains("session has expired");

        if (authError) {
            return new IllegalStateException(
                    "Facebook Access Token is expired or invalid. Update fb.access.token in config.properties. ("
                            + (message.isBlank() ? responseBody : message) + ")"
            );
        }
        return new RuntimeException("Facebook API error [HTTP " + statusCode + "]: " + responseBody);
    }

    private void validateCredentials() {
        if (!credentialsPresent()) {
            throw new IllegalStateException("Missing required fb.page.id or fb.access.token in config.properties");
        }
    }

    private boolean credentialsPresent() {
        return hasText(config.pageId()) && hasText(config.accessToken());
    }

    private boolean reachedPageLimit(int pageCount) {
        return config.maxPages() > 0 && pageCount >= config.maxPages();
    }

    private String withAccessToken(String url) {
        if (!hasText(url) || url.contains("access_token=") || !hasText(config.accessToken())) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?")
                + "access_token="
                + URLEncoder.encode(config.accessToken(), StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
