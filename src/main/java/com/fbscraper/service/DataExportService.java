package com.fbscraper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fbscraper.model.AnalyzedConversation;
import com.fbscraper.model.AnalyzedReview;
import com.fbscraper.model.CommentAnalysis;
import com.fbscraper.model.FacebookReaction;
import com.fbscraper.model.FacebookUser;
import com.fbscraper.model.MessageSentimentSummary;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.PostReactionAnalysis;
import com.fbscraper.model.ReactionSummary;
import com.fbscraper.model.SentimentLevel;
import com.fbscraper.model.SyncResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;

/**
 * Persists synchronization results into separate JSON files on disk and loads previous results.
 */
@Service
public class DataExportService {

    public record MessagesExport(
            Instant syncedAt,
            MessageSentimentSummary summary,
            List<AnalyzedConversation> conversations
    ) {
        public MessagesExport {
            summary = summary == null ? MessageSentimentSummary.EMPTY : summary;
            conversations = conversations == null ? List.of() : List.copyOf(conversations);
        }
    }

    public record PostReactionsExport(
            Instant syncedAt,
            int totalReactions,
            ReactionSummary reactionTotals,
            List<PostReactionAnalysis> posts
    ) {}

    public record CommentReactionsExport(
            int totalCommentReactions,
            ReactionSummary commentReactionTotals
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

    public void export(SyncResult result) {
        if (result == null) {
            return;
        }

        try {
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            // 1. Reviews
            Path reviewsFile = outputDir.resolve("reviews.json");
            objectMapper.writeValue(reviewsFile.toFile(), result.reviews());

            // 2. Ratings
            Path ratingsFile = outputDir.resolve("ratings.json");
            objectMapper.writeValue(ratingsFile.toFile(), result.pageRating());

            // 3. Comments
            Path commentsFile = outputDir.resolve("comments.json");
            objectMapper.writeValue(commentsFile.toFile(), result.comments());

            // 4. Reactions on Posts
            Path postReactionsFile = outputDir.resolve("post_reactions.json");
            PostReactionsExport postReactionsExport = new PostReactionsExport(
                    result.syncedAt(),
                    result.totalReactions(),
                    result.reactionTotals(),
                    result.postReactions()
            );
            objectMapper.writeValue(postReactionsFile.toFile(), postReactionsExport);

            // 5. Reactions on Comments
            Path commentReactionsFile = outputDir.resolve("comment_reactions.json");
            CommentReactionsExport commentReactionsExport = new CommentReactionsExport(
                    result.totalCommentReactions(),
                    result.commentReactionTotals()
            );
            objectMapper.writeValue(commentReactionsFile.toFile(), commentReactionsExport);

            // 6. Messages / Conversations
            if (result.conversations() != null) {
                Path messagesFile = outputDir.resolve("messages.json");
                MessagesExport messagesExport = new MessagesExport(
                        result.syncedAt(),
                        result.messageSummary(),
                        result.conversations()
                );
                objectMapper.writeValue(messagesFile.toFile(), messagesExport);
            }

            System.out.println("[DataExportService] Successfully exported sync data to: " + outputDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[DataExportService] Failed to export sync data: " + e.getMessage());
        }
    }

    public Optional<SyncResult> loadLatest(double negativeThreshold) {
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
            List<CommentAnalysis> comments = loadComments(commentsFile, negativeThreshold);
            List<AnalyzedReview> reviews = loadReviews(reviewsFile);
            MessagesExport messagesExport = loadMessages(messagesFile);

            if (comments.isEmpty() && reviews.isEmpty() && messagesExport.conversations().isEmpty()) {
                return Optional.empty();
            }

            PageRatingSummary pageRating = loadPageRating(ratingsFile, reviews);
            PostReactionsExport postReactionsExport = loadPostReactions(postReactionsFile, comments);
            CommentReactionsExport commentReactionsExport = loadCommentReactions(commentReactionsFile, comments);

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
            int negative = (int) comments.stream().filter(CommentAnalysis::flagged).count();
            int negativeReviews = (int) reviews.stream()
                    .filter(r -> r.score() != null && r.score().compound() <= negativeThreshold)
                    .count();

            double negativeRate = comments.isEmpty()
                    ? 0.0
                    : Math.round((negative * 1000.0) / comments.size()) / 10.0;

            List<PostReactionAnalysis> posts = postReactionsExport.posts() != null
                    ? postReactionsExport.posts()
                    : List.of();

            SyncResult result = new SyncResult(
                    syncedAt,
                    negativeThreshold,
                    posts.size(),
                    comments.size(),
                    postReactionsExport.totalReactions(),
                    postReactionsExport.reactionTotals() != null ? postReactionsExport.reactionTotals() : ReactionSummary.empty(),
                    commentReactionsExport.totalCommentReactions(),
                    commentReactionsExport.commentReactionTotals() != null ? commentReactionsExport.commentReactionTotals() : ReactionSummary.empty(),
                    positive,
                    neutral,
                    warning,
                    critical,
                    negative,
                    negativeRate,
                    comments,
                    posts,
                    pageRating != null ? pageRating : PageRatingSummary.EMPTY,
                    reviews.size(),
                    negativeReviews,
                    reviews,
                    messagesExport.summary(),
                    messagesExport.conversations()
            );

            System.out.println("[DataExportService] Pre-loaded previous sync data from: " + outputDir.toAbsolutePath()
                    + " (" + comments.size() + " comments, " + reviews.size() + " reviews, " + posts.size() + " posts)");
            return Optional.of(result);
        } catch (Exception e) {
            System.err.println("[DataExportService] Could not load previous data from disk: " + e.getMessage());
            return Optional.empty();
        }
    }

    private List<CommentAnalysis> loadComments(Path commentsFile, double negativeThreshold) {
        if (!Files.exists(commentsFile)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(commentsFile.toFile());
            if (!root.isArray()) {
                return List.of();
            }
            List<CommentAnalysis> result = new ArrayList<>();
            for (JsonNode node : root) {
                result.add(parseCommentAnalysisNode(node, negativeThreshold));
            }
            return result;
        } catch (Exception e) {
            System.err.println("[DataExportService] Failed to parse comments.json: " + e.getMessage());
            return List.of();
        }
    }

    private CommentAnalysis parseCommentAnalysisNode(JsonNode node, double negativeThreshold) {
        // Support both new CommentAnalysis format and legacy AnalyzedComment format
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
        ReactionSummary reactions = ReactionSummary.empty();
        if (reactionsNode != null && !reactionsNode.isNull() && !reactionsNode.isMissingNode()) {
            try {
                reactions = objectMapper.treeToValue(reactionsNode, ReactionSummary.class);
            } catch (Exception ignored) {}
        }
        if (reactions == null) {
            reactions = ReactionSummary.empty();
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

        List<CommentAnalysis> replies = new ArrayList<>();
        JsonNode repliesNode = commentNode.has("replies") ? commentNode.get("replies") : node.get("replies");
        if (repliesNode != null && repliesNode.isArray()) {
            for (JsonNode replyNode : repliesNode) {
                replies.add(parseCommentAnalysisNode(replyNode, negativeThreshold));
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

        return new CommentAnalysis(commentId, postId, postSnippet, message, createdTime, compound, level, flagged, reactions, from, replies, userReactions);
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

    private List<AnalyzedReview> loadReviews(Path reviewsFile) {
        if (!Files.exists(reviewsFile)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(reviewsFile.toFile(), new TypeReference<List<AnalyzedReview>>() {});
        } catch (Exception e) {
            System.err.println("[DataExportService] Failed to parse reviews.json: " + e.getMessage());
            return List.of();
        }
    }

    private PageRatingSummary loadPageRating(Path ratingsFile, List<AnalyzedReview> reviews) {
        PageRatingSummary pageRating = PageRatingSummary.EMPTY;
        if (Files.exists(ratingsFile)) {
            try {
                pageRating = objectMapper.readValue(ratingsFile.toFile(), PageRatingSummary.class);
            } catch (Exception e) {
                System.err.println("[DataExportService] Failed to parse ratings.json: " + e.getMessage());
            }
        }

        if (pageRating.yesRecommendations() == 0 && pageRating.noRecommendations() == 0 && !reviews.isEmpty()) {
            int yes = (int) reviews.stream().filter(r -> r.review() != null && r.review().isPositiveRecommendation()).count();
            int no = (int) reviews.stream().filter(r -> r.review() != null && r.review().isNegativeRecommendation()).count();
            int count = Math.max(pageRating.ratingCount(), reviews.size());
            double rating = pageRating.hasRatings() && pageRating.overallStarRating() > 0.0
                    ? pageRating.overallStarRating()
                    : Math.round(((double) yes / reviews.size()) * 50.0) / 10.0;
            pageRating = new PageRatingSummary(rating, count, yes, no);
        }
        return pageRating;
    }

    private PostReactionsExport loadPostReactions(Path postReactionsFile, List<CommentAnalysis> comments) {
        if (Files.exists(postReactionsFile)) {
            try {
                PostReactionsExport export = objectMapper.readValue(postReactionsFile.toFile(), PostReactionsExport.class);
                if (export.posts() != null && !export.posts().isEmpty()) {
                    return export;
                }
            } catch (Exception e) {
                System.err.println("[DataExportService] Failed to parse post_reactions.json: " + e.getMessage());
            }
        }

        // Fallback: derive posts from comments
        Map<String, PostReactionAnalysis> postMap = new LinkedHashMap<>();
        for (CommentAnalysis c : comments) {
            if (c.postId() != null && !c.postId().isBlank()) {
                postMap.putIfAbsent(c.postId(), new PostReactionAnalysis(
                        c.postId(),
                        c.postSnippet() != null && !c.postSnippet().isBlank() ? c.postSnippet() : "[Post " + c.postId() + "]",
                        c.createdTime() != null ? c.createdTime() : Instant.now(),
                        ReactionSummary.empty()
                ));
            }
        }
        List<PostReactionAnalysis> posts = new ArrayList<>(postMap.values());
        return new PostReactionsExport(null, 0, ReactionSummary.empty(), posts);
    }

    private CommentReactionsExport loadCommentReactions(Path commentReactionsFile, List<CommentAnalysis> comments) {
        if (Files.exists(commentReactionsFile)) {
            try {
                CommentReactionsExport export = objectMapper.readValue(commentReactionsFile.toFile(), CommentReactionsExport.class);
                if (export.totalCommentReactions() > 0 || (export.commentReactionTotals() != null && export.commentReactionTotals().total() > 0)) {
                    return export;
                }
            } catch (Exception e) {
                System.err.println("[DataExportService] Failed to parse comment_reactions.json: " + e.getMessage());
            }
        }

        // Fallback: sum reactions across comments
        int like = 0, love = 0, care = 0, haha = 0, wow = 0, sad = 0, angry = 0;
        for (CommentAnalysis c : comments) {
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
        ReactionSummary totals = new ReactionSummary(total, like, love, care, haha, wow, sad, angry);
        return new CommentReactionsExport(total, totals);
    }

    private MessagesExport loadMessages(Path messagesFile) {
        if (!Files.exists(messagesFile)) {
            return new MessagesExport(null, MessageSentimentSummary.EMPTY, List.of());
        }
        try {
            return objectMapper.readValue(messagesFile.toFile(), MessagesExport.class);
        } catch (Exception e) {
            System.err.println("[DataExportService] Failed to parse messages.json: " + e.getMessage());
            return new MessagesExport(null, MessageSentimentSummary.EMPTY, List.of());
        }
    }

    public Path outputDir() {
        return outputDir;
    }
}
