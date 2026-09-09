package com.fbscraper.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.Attachment;
import com.fbscraper.model.Comment;
import com.fbscraper.model.Conversation;
import com.fbscraper.model.Message;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.Post;
import com.fbscraper.model.Reaction;
import com.fbscraper.model.ReactionSummary;
import com.fbscraper.model.Review;
import com.fbscraper.model.User;

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

import org.springframework.stereotype.Component;

@Component
public class FacebookClient {

    public record FeedPage(List<Post> posts, String nextUrl) {
    }

    public record ReviewPage(List<Review> reviews, String nextUrl) {
    }

    public record ConversationPage(List<Conversation> conversations, String nextUrl) {
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

    @org.springframework.beans.factory.annotation.Autowired
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

    public List<Post> fetchPageFeed() {
        validateCredentials();

        List<Post> allPosts = new ArrayList<>();
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
        List<Post> posts = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode postNode : data) {
                    List<Comment> comments = new ArrayList<>();
                    JsonNode commentData = postNode.path("comments").path("data");
                    if (commentData.isArray()) {
                        for (JsonNode commentNode : commentData) {
                            comments.add(parseComment(commentNode));
                        }
                    }
                    List<Reaction> userReactions = parseUserReactions(postNode.path("reactions").path("data"));

                    posts.add(new Post(
                            postNode.path("id").asText(""),
                            postNode.path("message").asText(""),
                            parseInstant(postNode.path("created_time").asText("")),
                            comments,
                            parseReactions(postNode),
                            userReactions
                    ));
                }
            }
            return new FeedPage(posts, pagingNext(root));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Facebook feed JSON", e);
        }
    }

    private Comment parseComment(JsonNode commentNode) {
        String id = commentNode.path("id").asText("");
        String message = commentNode.path("message").asText("");
        Instant createdTime = parseInstant(commentNode.path("created_time").asText(""));
        ReactionSummary reactions = parseReactions(commentNode);
        User from = parseUser(commentNode.path("from"));
        List<Reaction> userReactions = parseUserReactions(commentNode.path("reactions").path("data"));

        List<Comment> replies = new ArrayList<>();
        JsonNode replyData = commentNode.path("comments").path("data");
        if (replyData.isArray()) {
            for (JsonNode replyNode : replyData) {
                replies.add(parseComment(replyNode));
            }
        }

        return new Comment(id, message, createdTime, reactions, from, replies, userReactions);
    }

    private User parseUser(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return User.ANONYMOUS;
        }
        String id = node.path("id").asText("");
        String name = node.path("name").asText("");
        return new User(id, name);
    }

    private List<Reaction> parseUserReactions(JsonNode data) {
        List<Reaction> list = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                String id = node.path("id").asText("");
                String name = node.path("name").asText("");
                String type = node.path("type").asText("LIKE");
                list.add(new Reaction(id, name, type));
            }
        }
        return list;
    }

    public String buildFeedUrl() {
        String nestedCommentsField = String.format(
                "comments.limit(%d){id,message,created_time,from{id,name},reactions.limit(%d){id,name,type},%s}",
                config.nestedCommentLimit(),
                config.reactionLimit(),
                REACTION_FIELDS
        );
        String topLevelCommentsField = String.format(
                "comments.limit(%d){id,message,created_time,from{id,name},reactions.limit(%d){id,name,type},%s,%s}",
                config.commentLimit(),
                config.reactionLimit(),
                REACTION_FIELDS,
                nestedCommentsField
        );
        String fields = String.format(
                "id,message,created_time,permalink_url,reactions.limit(%d){id,name,type},%s,%s",
                config.reactionLimit(),
                REACTION_FIELDS,
                topLevelCommentsField
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

    public List<Review> fetchPageReviews() {
        if (!credentialsPresent()) {
            return List.of();
        }

        List<Review> allReviews = new ArrayList<>();
        String fields = "created_time,recommendation_type,review_text,rating,has_review,reviewer{id,name}";
        String currentUrl = withAccessToken(String.format(
                "https://graph.facebook.com/%s/%s/ratings?fields=%s&limit=%d",
                config.apiVersion(),
                config.pageId(),
                URLEncoder.encode(fields, StandardCharsets.UTF_8),
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
        List<Review> reviews = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    String reviewText = item.path("review_text").asText("");
                    User reviewer = parseUser(item.path("reviewer"));
                    reviews.add(new Review(
                            parseInstant(item.path("created_time").asText("")),
                            item.hasNonNull("recommendation_type")
                                    ? item.path("recommendation_type").asText("")
                                    : null,
                            reviewText,
                            item.path("rating").asInt(0),
                            item.path("has_review").asBoolean(!reviewText.isBlank()),
                            reviewer
                    ));
                }
            }
            return new ReviewPage(reviews, pagingNext(root));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Facebook reviews JSON", e);
        }
    }

    public List<Conversation> fetchPageConversations() {
        if (!credentialsPresent()) {
            return List.of();
        }

        List<Conversation> allConversations = new ArrayList<>();
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
        List<Conversation> conversations = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode convNode : data) {
                    List<User> participants = parseParticipants(convNode.path("participants").path("data"));
                    List<Message> messages = parseMessages(convNode.path("messages").path("data"));
                    conversations.add(new Conversation(
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

    private List<User> parseParticipants(JsonNode data) {
        List<User> list = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                list.add(new User(
                        node.path("id").asText(""),
                        node.path("name").asText(""),
                        node.hasNonNull("email") ? node.path("email").asText("") : null
                ));
            }
        }
        return list;
    }

    private List<Message> parseMessages(JsonNode data) {
        List<Message> list = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode node : data) {
                User from = null;
                if (node.hasNonNull("from")) {
                    JsonNode fromNode = node.get("from");
                    from = new User(
                            fromNode.path("id").asText(""),
                            fromNode.path("name").asText(""),
                            fromNode.hasNonNull("email") ? fromNode.path("email").asText("") : null
                    );
                }
                List<User> to = parseParticipants(node.path("to").path("data"));
                List<Attachment> attachments = parseAttachments(node.path("attachments").path("data"));
                list.add(new Message(
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

    private List<Attachment> parseAttachments(JsonNode data) {
        List<Attachment> list = new ArrayList<>();
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

                list.add(new Attachment(id, mimeType, name, size, url, previewUrl));
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
