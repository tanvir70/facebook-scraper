package com.fbscraper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.enums.SentimentLevel;
import com.fbscraper.model.MessageSentimentSummary;
import com.fbscraper.model.facebook.FacebookCommentAnalysis;
import com.fbscraper.model.facebook.FacebookConversation;
import com.fbscraper.model.facebook.FacebookPageRatingSummary;
import com.fbscraper.model.facebook.FacebookPostReactionAnalysis;
import com.fbscraper.model.facebook.FacebookReaction;
import com.fbscraper.model.facebook.FacebookReactionSummary;
import com.fbscraper.model.facebook.FacebookReview;
import com.fbscraper.model.facebook.FacebookSyncResult;
import com.fbscraper.model.facebook.FacebookUser;
import com.fbscraper.model.instagram.InstagramCommentAnalysis;
import com.fbscraper.model.instagram.InstagramConversation;
import com.fbscraper.model.instagram.InstagramMediaAnalysis;
import com.fbscraper.model.instagram.InstagramSyncResult;
import com.fbscraper.model.instagram.InstagramUser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DataExportService {

    public record FacebookMessagesExport(
            Instant syncedAt,
            MessageSentimentSummary summary,
            List<FacebookConversation> conversations
    ) {
        public FacebookMessagesExport {
            summary = summary == null ? MessageSentimentSummary.EMPTY : summary;
            conversations = conversations == null ? List.of() : List.copyOf(conversations);
        }
    }

    public record FacebookPostReactionsExport(
            Instant syncedAt,
            int totalReactions,
            FacebookReactionSummary reactionTotals,
            List<FacebookPostReactionAnalysis> posts
    ) {}

    public record FacebookCommentReactionsExport(
            int totalCommentReactions,
            FacebookReactionSummary commentReactionTotals
    ) {}

    public record InstagramMessagesExport(
            Instant syncedAt,
            MessageSentimentSummary summary,
            List<InstagramConversation> conversations
    ) {
        public InstagramMessagesExport {
            summary = summary == null ? MessageSentimentSummary.EMPTY : summary;
            conversations = conversations == null ? List.of() : List.copyOf(conversations);
        }
    }

    public record InstagramMediaExport(
            Instant syncedAt,
            int totalMedia,
            int totalLikes,
            List<InstagramMediaAnalysis> media
    ) {}

    private final Path outputDir;
    private final ObjectMapper objectMapper;

    public DataExportService() {
        this(Path.of("output"));
    }

    public DataExportService(Path outputDir) {
        this.outputDir = outputDir;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void export(FacebookSyncResult result) {
        exportFacebook(result);
    }

    public void exportFacebook(FacebookSyncResult result) {
        if (result == null) {
            return;
        }

        try {
            ensureDirectory();

            Path reviewsFile = outputDir.resolve("reviews.json");
            objectMapper.writeValue(reviewsFile.toFile(), result.reviews());

            Path ratingsFile = outputDir.resolve("ratings.json");
            objectMapper.writeValue(ratingsFile.toFile(), result.pageRating());

            Path commentsFile = outputDir.resolve("comments.json");
            objectMapper.writeValue(commentsFile.toFile(), result.comments());

            Path postReactionsFile = outputDir.resolve("post_reactions.json");
            FacebookPostReactionsExport postReactionsExport = new FacebookPostReactionsExport(
                    result.syncedAt(),
                    result.totalReactions(),
                    result.reactionTotals(),
                    result.postReactions()
            );
            objectMapper.writeValue(postReactionsFile.toFile(), postReactionsExport);

            Path commentReactionsFile = outputDir.resolve("comment_reactions.json");
            FacebookCommentReactionsExport commentReactionsExport = new FacebookCommentReactionsExport(
                    result.totalCommentReactions(),
                    result.commentReactionTotals()
            );
            objectMapper.writeValue(commentReactionsFile.toFile(), commentReactionsExport);

            if (result.conversations() != null) {
                Path messagesFile = outputDir.resolve("messages.json");
                FacebookMessagesExport messagesExport = new FacebookMessagesExport(
                        result.syncedAt(),
                        result.messageSummary(),
                        result.conversations()
                );
                objectMapper.writeValue(messagesFile.toFile(), messagesExport);
            }

            Path syncResultFile = outputDir.resolve("sync-result.json");
            objectMapper.writeValue(syncResultFile.toFile(), result);

            System.out.println("[DataExportService] Successfully exported Facebook sync data to: " + outputDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[DataExportService] Failed to export Facebook sync data: " + e.getMessage());
        }
    }

    public void exportInstagram(InstagramSyncResult result) {
        if (result == null) {
            return;
        }

        try {
            ensureDirectory();

            Path commentsFile = outputDir.resolve("instagram_comments.json");
            objectMapper.writeValue(commentsFile.toFile(), result.comments());

            Path mediaFile = outputDir.resolve("instagram_media.json");
            InstagramMediaExport mediaExport = new InstagramMediaExport(
                    result.syncedAt(),
                    result.totalMedia(),
                    result.totalLikes(),
                    result.media()
            );
            objectMapper.writeValue(mediaFile.toFile(), mediaExport);

            if (result.conversations() != null) {
                Path messagesFile = outputDir.resolve("instagram_messages.json");
                InstagramMessagesExport messagesExport = new InstagramMessagesExport(
                        result.syncedAt(),
                        result.messageSummary(),
                        result.conversations()
                );
                objectMapper.writeValue(messagesFile.toFile(), messagesExport);
            }

            Path syncResultFile = outputDir.resolve("instagram_sync-result.json");
            objectMapper.writeValue(syncResultFile.toFile(), result);

            System.out.println("[DataExportService] Successfully exported Instagram sync data to: " + outputDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[DataExportService] Failed to export Instagram sync data: " + e.getMessage());
        }
    }

    public Optional<FacebookSyncResult> loadLatest(double negativeThreshold) {
        return loadLatestFacebook(negativeThreshold);
    }

    public Optional<FacebookSyncResult> loadLatestFacebook(double negativeThreshold) {
        Path syncResultFile = outputDir.resolve("sync-result.json");
        if (Files.exists(syncResultFile)) {
            try {
                FacebookSyncResult result = objectMapper.readValue(syncResultFile.toFile(), FacebookSyncResult.class);
                if (result != null) {
                    return Optional.of(result);
                }
            } catch (Exception ignored) {
            }
        }

        Path commentsFile = outputDir.resolve("comments.json");
        Path reviewsFile = outputDir.resolve("reviews.json");
        Path ratingsFile = outputDir.resolve("ratings.json");
        Path postReactionsFile = outputDir.resolve("post_reactions.json");
        Path commentReactionsFile = outputDir.resolve("comment_reactions.json");
        Path messagesFile = outputDir.resolve("messages.json");

        if (!Files.exists(commentsFile) && !Files.exists(reviewsFile) && !Files.exists(messagesFile)) {
            return Optional.empty();
        }

        try {
            List<FacebookCommentAnalysis> comments = loadFacebookComments(commentsFile, negativeThreshold);
            List<FacebookReview> reviews = loadFacebookReviews(reviewsFile);
            FacebookMessagesExport messagesExport = loadFacebookMessages(messagesFile);

            if (comments.isEmpty() && reviews.isEmpty() && messagesExport.conversations().isEmpty()) {
                return Optional.empty();
            }

            FacebookPageRatingSummary pageRating = loadFacebookPageRating(ratingsFile, reviews);
            FacebookPostReactionsExport postReactionsExport = loadFacebookPostReactions(postReactionsFile, comments);
            FacebookCommentReactionsExport commentReactionsExport = loadFacebookCommentReactions(commentReactionsFile, comments);

            Instant syncedAt = postReactionsExport.syncedAt() != null
                    ? postReactionsExport.syncedAt()
                    : (messagesExport.syncedAt() != null
                    ? messagesExport.syncedAt()
                    : (Files.exists(commentsFile)
                    ? Files.getLastModifiedTime(commentsFile).toInstant()
                    : (Files.exists(reviewsFile) ? Files.getLastModifiedTime(reviewsFile).toInstant() : Instant.now())));

            int positive = (int) comments.stream().filter(c -> c.level() == SentimentLevel.POSITIVE).count();
            int neutral = (int) comments.stream().filter(c -> c.level() == SentimentLevel.NEUTRAL).count();
            int warning = (int) comments.stream().filter(c -> c.level() == SentimentLevel.WARNING_NEGATIVE).count();
            int critical = (int) comments.stream().filter(c -> c.level() == SentimentLevel.CRITICAL_NEGATIVE).count();
            int negative = (int) comments.stream().filter(FacebookCommentAnalysis::flagged).count();
            int negativeReviews = (int) reviews.stream()
                    .filter(r -> r.score() != null && r.score().compound() <= negativeThreshold)
                    .count();

            double negativeRate = comments.isEmpty()
                    ? 0.0
                    : Math.round((negative * 1000.0) / comments.size()) / 10.0;

            List<FacebookPostReactionAnalysis> posts = postReactionsExport.posts() != null
                    ? postReactionsExport.posts()
                    : List.of();

            FacebookSyncResult result = new FacebookSyncResult(
                    syncedAt,
                    negativeThreshold,
                    posts.size(),
                    comments.size(),
                    postReactionsExport.totalReactions(),
                    postReactionsExport.reactionTotals() != null ? postReactionsExport.reactionTotals() : FacebookReactionSummary.empty(),
                    commentReactionsExport.totalCommentReactions(),
                    commentReactionsExport.commentReactionTotals() != null ? commentReactionsExport.commentReactionTotals() : FacebookReactionSummary.empty(),
                    positive,
                    neutral,
                    warning,
                    critical,
                    negative,
                    negativeRate,
                    comments,
                    posts,
                    pageRating != null ? pageRating : FacebookPageRatingSummary.EMPTY,
                    reviews.size(),
                    negativeReviews,
                    reviews,
                    messagesExport.summary(),
                    messagesExport.conversations()
            );

            System.out.println("[DataExportService] Pre-loaded previous Facebook data from: " + outputDir.toAbsolutePath()
                    + " (" + comments.size() + " comments, " + reviews.size() + " reviews, " + posts.size() + " posts)");
            return Optional.of(result);
        } catch (Exception e) {
            System.err.println("[DataExportService] Could not load previous Facebook data from disk: " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<InstagramSyncResult> loadLatestInstagram(double negativeThreshold) {
        Path syncResultFile = outputDir.resolve("instagram_sync-result.json");
        if (Files.exists(syncResultFile)) {
            try {
                InstagramSyncResult result = objectMapper.readValue(syncResultFile.toFile(), InstagramSyncResult.class);
                if (result != null) {
                    return Optional.of(result);
                }
            } catch (Exception ignored) {
            }
        }

        Path commentsFile = outputDir.resolve("instagram_comments.json");
        Path mediaFile = outputDir.resolve("instagram_media.json");
        Path messagesFile = outputDir.resolve("instagram_messages.json");

        if (!Files.exists(commentsFile) && !Files.exists(mediaFile) && !Files.exists(messagesFile)) {
            return Optional.empty();
        }

        try {
            List<InstagramCommentAnalysis> comments = loadInstagramComments(commentsFile, negativeThreshold);
            InstagramMediaExport mediaExport = loadInstagramMedia(mediaFile);
            InstagramMessagesExport messagesExport = loadInstagramMessages(messagesFile);

            if (comments.isEmpty() && mediaExport.media().isEmpty() && messagesExport.conversations().isEmpty()) {
                return Optional.empty();
            }

            Instant syncedAt = mediaExport.syncedAt() != null
                    ? mediaExport.syncedAt()
                    : (messagesExport.syncedAt() != null
                    ? messagesExport.syncedAt()
                    : (Files.exists(commentsFile) ? Files.getLastModifiedTime(commentsFile).toInstant() : Instant.now()));

            int positive = (int) comments.stream().filter(c -> c.level() == SentimentLevel.POSITIVE).count();
            int neutral = (int) comments.stream().filter(c -> c.level() == SentimentLevel.NEUTRAL).count();
            int warning = (int) comments.stream().filter(c -> c.level() == SentimentLevel.WARNING_NEGATIVE).count();
            int critical = (int) comments.stream().filter(c -> c.level() == SentimentLevel.CRITICAL_NEGATIVE).count();
            int negative = (int) comments.stream().filter(InstagramCommentAnalysis::flagged).count();
            double negativeRate = comments.isEmpty()
                    ? 0.0
                    : Math.round((negative * 1000.0) / comments.size()) / 10.0;

            InstagramSyncResult result = new InstagramSyncResult(
                    syncedAt,
                    negativeThreshold,
                    mediaExport.totalMedia(),
                    comments.size(),
                    mediaExport.totalLikes(),
                    positive,
                    neutral,
                    warning,
                    critical,
                    negative,
                    negativeRate,
                    comments,
                    mediaExport.media(),
                    messagesExport.summary(),
                    messagesExport.conversations()
            );

            System.out.println("[DataExportService] Pre-loaded previous Instagram data from: " + outputDir.toAbsolutePath()
                    + " (" + comments.size() + " comments, " + mediaExport.totalMedia() + " media)");
            return Optional.of(result);
        } catch (Exception e) {
            System.err.println("[DataExportService] Could not load previous Instagram data from disk: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void ensureDirectory() throws IOException {
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
    }

    private List<FacebookCommentAnalysis> loadFacebookComments(Path commentsFile, double negativeThreshold) {
        if (!Files.exists(commentsFile)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(commentsFile.toFile());
            if (!root.isArray()) {
                return List.of();
            }
            List<FacebookCommentAnalysis> result = new ArrayList<>();
            for (JsonNode node : root) {
                result.add(parseFacebookCommentAnalysisNode(node, negativeThreshold));
            }
            return result;
        } catch (Exception e) {
            System.err.println("[DataExportService] Failed to parse comments.json: " + e.getMessage());
            return List.of();
        }
    }

    private FacebookCommentAnalysis parseFacebookCommentAnalysisNode(JsonNode node, double negativeThreshold) {
        JsonNode commentNode = node.has("comment") ? node.get("comment") : node;

        String commentId = commentNode.path("id").asText(node.path("commentId").asText(""));
        String message = commentNode.path("message").asText(node.path("message").asText(""));

        JsonNode createdTimeNode = commentNode.has("createdTime") ? commentNode.get("createdTime") : node.get("createdTime");
        Instant createdTime = parseInstant(createdTimeNode);

        String postId = node.path("postId").asText("");
        String postSnippet = node.path("postSnippet").asText("");

        double compound = node.has("score")
                ? node.path("score").path("compound").asDouble(0.0)
                : node.path("compound").asDouble(0.0);

        String levelStr = node.has("score")
                ? node.path("score").path("level").asText("")
                : node.path("level").asText("");
        SentimentLevel level = parseSentimentLevel(levelStr, compound, negativeThreshold);

        boolean flagged = node.has("flagged")
                ? node.path("flagged").asBoolean(compound <= negativeThreshold)
                : (compound <= negativeThreshold);

        JsonNode reactionsNode = commentNode.has("reactions") ? commentNode.get("reactions") : node.get("reactions");
        FacebookReactionSummary reactions = FacebookReactionSummary.empty();
        if (reactionsNode != null && !reactionsNode.isNull() && !reactionsNode.isMissingNode()) {
            try {
                reactions = objectMapper.treeToValue(reactionsNode, FacebookReactionSummary.class);
            } catch (Exception ignored) {}
        }
        if (reactions == null) {
            reactions = FacebookReactionSummary.empty();
        }

        JsonNode fromNode = commentNode.has("from") ? commentNode.get("from") : node.get("from");
        FacebookUser from = FacebookUser.ANONYMOUS;
        if (fromNode != null && !fromNode.isNull() && !fromNode.isMissingNode()) {
            try {
                from = objectMapper.treeToValue(fromNode, FacebookUser.class);
            } catch (Exception ignored) {}
        }
        if (from == null) {
            from = FacebookUser.ANONYMOUS;
        }

        List<FacebookCommentAnalysis> replies = new ArrayList<>();
        JsonNode repliesNode = commentNode.has("replies") ? commentNode.get("replies") : node.get("replies");
        if (repliesNode != null && repliesNode.isArray()) {
            for (JsonNode replyNode : repliesNode) {
                replies.add(parseFacebookCommentAnalysisNode(replyNode, negativeThreshold));
            }
        }

        List<FacebookReaction> userReactions = new ArrayList<>();
        JsonNode userReactionsNode = commentNode.has("userReactions") ? commentNode.get("userReactions") : node.get("userReactions");
        if (userReactionsNode != null && userReactionsNode.isArray()) {
            for (JsonNode rNode : userReactionsNode) {
                try {
                    FacebookReaction reaction = objectMapper.treeToValue(rNode, FacebookReaction.class);
                    if (reaction != null) {
                        userReactions.add(reaction);
                    }
                } catch (Exception ignored) {}
            }
        }

        return new FacebookCommentAnalysis(commentId, postId, postSnippet, message, createdTime, compound, level, flagged, reactions, from, replies, userReactions);
    }

    private List<InstagramCommentAnalysis> loadInstagramComments(Path commentsFile, double negativeThreshold) {
        if (!Files.exists(commentsFile)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(commentsFile.toFile());
            if (!root.isArray()) {
                return List.of();
            }
            List<InstagramCommentAnalysis> result = new ArrayList<>();
            for (JsonNode node : root) {
                result.add(parseInstagramCommentAnalysisNode(node, negativeThreshold));
            }
            return result;
        } catch (Exception e) {
            System.err.println("[DataExportService] Failed to parse instagram_comments.json: " + e.getMessage());
            return List.of();
        }
    }

    private InstagramCommentAnalysis parseInstagramCommentAnalysisNode(JsonNode node, double negativeThreshold) {
        String id = node.path("id").asText("");
        String mediaId = node.path("mediaId").asText("");
        String mediaSnippet = node.path("mediaSnippet").asText("");
        String text = node.path("text").asText(node.path("message").asText(""));

        Instant timestamp = parseInstant(node.get("timestamp") != null ? node.get("timestamp") : node.get("createdTime"));

        double score = node.has("score") && node.get("score").isNumber()
                ? node.path("score").asDouble(0.0)
                : node.path("compound").asDouble(0.0);

        String levelStr = node.path("level").asText("");
        SentimentLevel level = parseSentimentLevel(levelStr, score, negativeThreshold);

        boolean flagged = node.has("flagged")
                ? node.path("flagged").asBoolean(score <= negativeThreshold)
                : (score <= negativeThreshold);

        int likeCount = node.path("likeCount").asInt(0);

        InstagramUser from = InstagramUser.ANONYMOUS;
        JsonNode fromNode = node.get("from");
        if (fromNode != null && !fromNode.isNull() && !fromNode.isMissingNode()) {
            try {
                from = objectMapper.treeToValue(fromNode, InstagramUser.class);
            } catch (Exception ignored) {}
        }
        if (from == null) {
            from = InstagramUser.ANONYMOUS;
        }

        List<InstagramCommentAnalysis> replies = new ArrayList<>();
        JsonNode repliesNode = node.get("replies");
        if (repliesNode != null && repliesNode.isArray()) {
            for (JsonNode replyNode : repliesNode) {
                replies.add(parseInstagramCommentAnalysisNode(replyNode, negativeThreshold));
            }
        }

        return new InstagramCommentAnalysis(id, mediaId, mediaSnippet, text, timestamp, score, level, flagged, likeCount, from, replies);
    }

    private List<FacebookReview> loadFacebookReviews(Path reviewsFile) {
        if (!Files.exists(reviewsFile)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(reviewsFile.toFile(), new TypeReference<List<FacebookReview>>() {});
        } catch (Exception e) {
            System.err.println("[DataExportService] Failed to parse reviews.json: " + e.getMessage());
            return List.of();
        }
    }

    private FacebookPageRatingSummary loadFacebookPageRating(Path ratingsFile, List<FacebookReview> reviews) {
        FacebookPageRatingSummary pageRating = FacebookPageRatingSummary.EMPTY;
        if (Files.exists(ratingsFile)) {
            try {
                pageRating = objectMapper.readValue(ratingsFile.toFile(), FacebookPageRatingSummary.class);
            } catch (Exception e) {
                System.err.println("[DataExportService] Failed to parse ratings.json: " + e.getMessage());
            }
        }

        if (pageRating.yesRecommendations() == 0 && pageRating.noRecommendations() == 0 && !reviews.isEmpty()) {
            int yes = (int) reviews.stream().filter(FacebookReview::isPositiveRecommendation).count();
            int no = (int) reviews.stream().filter(FacebookReview::isNegativeRecommendation).count();
            int count = Math.max(pageRating.ratingCount(), reviews.size());
            double rating = pageRating.hasRatings() && pageRating.overallStarRating() > 0.0
                    ? pageRating.overallStarRating()
                    : Math.round(((double) yes / reviews.size()) * 50.0) / 10.0;
            pageRating = new FacebookPageRatingSummary(rating, count, yes, no);
        }
        return pageRating;
    }

    private FacebookPostReactionsExport loadFacebookPostReactions(Path postReactionsFile, List<FacebookCommentAnalysis> comments) {
        if (Files.exists(postReactionsFile)) {
            try {
                FacebookPostReactionsExport export = objectMapper.readValue(postReactionsFile.toFile(), FacebookPostReactionsExport.class);
                if (export.posts() != null && !export.posts().isEmpty()) {
                    return export;
                }
            } catch (Exception e) {
                System.err.println("[DataExportService] Failed to parse post_reactions.json: " + e.getMessage());
            }
        }

        Map<String, FacebookPostReactionAnalysis> postMap = new LinkedHashMap<>();
        for (FacebookCommentAnalysis c : comments) {
            if (c.postId() != null && !c.postId().isBlank()) {
                postMap.putIfAbsent(c.postId(), new FacebookPostReactionAnalysis(
                        c.postId(),
                        c.postSnippet() != null && !c.postSnippet().isBlank() ? c.postSnippet() : "[Post " + c.postId() + "]",
                        c.createdTime() != null ? c.createdTime() : Instant.now(),
                        FacebookReactionSummary.empty()
                ));
            }
        }
        List<FacebookPostReactionAnalysis> posts = new ArrayList<>(postMap.values());
        return new FacebookPostReactionsExport(null, 0, FacebookReactionSummary.empty(), posts);
    }

    private FacebookCommentReactionsExport loadFacebookCommentReactions(Path commentReactionsFile, List<FacebookCommentAnalysis> comments) {
        if (Files.exists(commentReactionsFile)) {
            try {
                FacebookCommentReactionsExport export = objectMapper.readValue(commentReactionsFile.toFile(), FacebookCommentReactionsExport.class);
                if (export.totalCommentReactions() > 0 || (export.commentReactionTotals() != null && export.commentReactionTotals().total() > 0)) {
                    return export;
                }
            } catch (Exception e) {
                System.err.println("[DataExportService] Failed to parse comment_reactions.json: " + e.getMessage());
            }
        }

        int like = 0, love = 0, care = 0, haha = 0, wow = 0, sad = 0, angry = 0;
        for (FacebookCommentAnalysis c : comments) {
            if (c.reactions() != null) {
                like += c.reactions().like();
                love += c.reactions().love();
                care += c.reactions().care();
                haha += c.reactions().haha();
                wow += c.reactions().wow();
                sad += c.reactions().sad();
                angry += c.reactions().angry();
            }
        }
        int total = like + love + care + haha + wow + sad + angry;
        FacebookReactionSummary totals = new FacebookReactionSummary(total, like, love, care, haha, wow, sad, angry);
        return new FacebookCommentReactionsExport(total, totals);
    }

    private FacebookMessagesExport loadFacebookMessages(Path messagesFile) {
        if (!Files.exists(messagesFile)) {
            return new FacebookMessagesExport(null, MessageSentimentSummary.EMPTY, List.of());
        }
        try {
            return objectMapper.readValue(messagesFile.toFile(), FacebookMessagesExport.class);
        } catch (Exception e) {
            System.err.println("[DataExportService] Failed to parse messages.json: " + e.getMessage());
            return new FacebookMessagesExport(null, MessageSentimentSummary.EMPTY, List.of());
        }
    }

    private InstagramMediaExport loadInstagramMedia(Path mediaFile) {
        if (!Files.exists(mediaFile)) {
            return new InstagramMediaExport(null, 0, 0, List.of());
        }
        try {
            return objectMapper.readValue(mediaFile.toFile(), InstagramMediaExport.class);
        } catch (Exception e) {
            System.err.println("[DataExportService] Failed to parse instagram_media.json: " + e.getMessage());
            return new InstagramMediaExport(null, 0, 0, List.of());
        }
    }

    private InstagramMessagesExport loadInstagramMessages(Path messagesFile) {
        if (!Files.exists(messagesFile)) {
            return new InstagramMessagesExport(null, MessageSentimentSummary.EMPTY, List.of());
        }
        try {
            return objectMapper.readValue(messagesFile.toFile(), InstagramMessagesExport.class);
        } catch (Exception e) {
            System.err.println("[DataExportService] Failed to parse instagram_messages.json: " + e.getMessage());
            return new InstagramMessagesExport(null, MessageSentimentSummary.EMPTY, List.of());
        }
    }

    private Instant parseInstant(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Instant.now();
        }
        if (node.isNumber()) {
            double val = node.asDouble();
            if (val > 1e11) {
                return Instant.ofEpochMilli((long) val);
            }
            long s = (long) val;
            long ns = (long) Math.round((val - s) * 1_000_000_000L);
            return Instant.ofEpochSecond(s, ns);
        }
        if (node.isTextual()) {
            try {
                return Instant.parse(node.asText());
            } catch (Exception ignored) {}
        }
        return Instant.now();
    }

    private SentimentLevel parseSentimentLevel(String levelStr, double compound, double negativeThreshold) {
        if (levelStr != null && !levelStr.isBlank()) {
            try {
                return SentimentLevel.valueOf(levelStr);
            } catch (IllegalArgumentException ignored) {}
        }
        if (compound <= -0.5) {
            return SentimentLevel.CRITICAL_NEGATIVE;
        } else if (compound <= negativeThreshold) {
            return SentimentLevel.WARNING_NEGATIVE;
        } else if (compound >= 0.05) {
            return SentimentLevel.POSITIVE;
        } else {
            return SentimentLevel.NEUTRAL;
        }
    }

    public Path outputDir() {
        return outputDir;
    }
}
