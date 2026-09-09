package com.fbscraper.service;

import com.fbscraper.client.FacebookClient;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.AnalyzedConversation;
import com.fbscraper.model.AnalyzedMessage;
import com.fbscraper.model.CommentAnalysis;
import com.fbscraper.model.AnalyzedReview;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookConversation;
import com.fbscraper.model.FacebookMessage;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.FacebookReview;
import com.fbscraper.model.MessageSentimentSummary;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.PostReactionAnalysis;
import com.fbscraper.model.ReactionSummary;
import com.fbscraper.model.SentimentLevel;
import com.fbscraper.model.SentimentScore;
import com.fbscraper.model.SyncResult;
import com.fbscraper.sentiment.VaderAnalyzer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Runs one complete synchronization: fetch Page comments and reactions, then analyze the comments.
 */
@Service
public class SentimentSyncService {

    private final FacebookClient facebookClient;
    private final VaderAnalyzer analyzer;
    private final AppConfig config;
    private final DataExportService dataExportService;

    public SentimentSyncService(AppConfig config) {
        this(config, new FacebookClient(config), VaderAnalyzer.createDefault(), new DataExportService());
    }

    public SentimentSyncService(AppConfig config, FacebookClient facebookClient, VaderAnalyzer analyzer) {
        this(config, facebookClient, analyzer, new DataExportService());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SentimentSyncService(AppConfig config, FacebookClient facebookClient, VaderAnalyzer analyzer, DataExportService dataExportService) {
        this.config = config;
        this.facebookClient = facebookClient;
        this.analyzer = analyzer;
        this.dataExportService = dataExportService;
    }

    public Optional<SyncResult> loadPreviousResult() {
        return dataExportService.loadLatest(config.negativeThreshold());
    }

    public DataExportService dataExportService() {
        return dataExportService;
    }

    public SyncResult sync() {
        List<FacebookPost> posts = facebookClient.fetchPageFeed();
        PageRatingSummary initialRating = facebookClient.fetchPageRatingSummary();
        List<FacebookReview> fetchedReviews = facebookClient.fetchPageReviews();
        List<FacebookConversation> fetchedConversations = facebookClient.fetchPageConversations();
        List<CommentAnalysis> comments = new ArrayList<>();
        List<PostReactionAnalysis> postReactions = new ArrayList<>();
        List<AnalyzedReview> reviews = new ArrayList<>();
        List<AnalyzedConversation> conversations = new ArrayList<>();

        int totalCustomerMessages = 0;
        int totalPageReplies = 0;
        int msgPositive = 0;
        int msgNeutral = 0;
        int msgWarning = 0;
        int msgCritical = 0;

        for (FacebookConversation conv : fetchedConversations) {
            List<AnalyzedMessage> analyzedMessages = new ArrayList<>();
            int custCount = 0;
            int pageCount = 0;
            SentimentLevel worstLevel = null;

            List<FacebookMessage> sortedMessages = new ArrayList<>(conv.messages());
            sortedMessages.sort(Comparator.comparing(FacebookMessage::createdTime));

            for (FacebookMessage msg : sortedMessages) {
                boolean isFromPage = msg.from() != null
                        && config.pageId() != null
                        && !config.pageId().isBlank()
                        && config.pageId().equals(msg.from().id());

                if (isFromPage) {
                    pageCount++;
                    totalPageReplies++;
                    analyzedMessages.add(new AnalyzedMessage(msg, true, null, false));
                } else {
                    custCount++;
                    totalCustomerMessages++;
                    SentimentScore score = analyzer.analyze(msg.message());
                    boolean flagged = score.compound() <= config.negativeThreshold();

                    switch (score.level()) {
                        case POSITIVE -> msgPositive++;
                        case NEUTRAL -> msgNeutral++;
                        case WARNING_NEGATIVE -> msgWarning++;
                        case CRITICAL_NEGATIVE -> msgCritical++;
                    }

                    worstLevel = prioritizeSentiment(worstLevel, score.level());
                    analyzedMessages.add(new AnalyzedMessage(msg, false, score, flagged));
                }
            }

            SentimentLevel threadSentiment = worstLevel != null ? worstLevel : SentimentLevel.NEUTRAL;
            conversations.add(new AnalyzedConversation(
                    conv.id(),
                    conv.updatedTime(),
                    conv.participants(),
                    analyzedMessages,
                    threadSentiment,
                    custCount,
                    pageCount
            ));
        }

        conversations.sort(Comparator.comparing(AnalyzedConversation::updatedTime).reversed());

        int totalMsgNegative = msgWarning + msgCritical;
        double messageNegativeRate = totalCustomerMessages == 0
                ? 0.0
                : Math.round((totalMsgNegative * 1000.0) / totalCustomerMessages) / 10.0;

        MessageSentimentSummary messageSummary = new MessageSentimentSummary(
                conversations.size(),
                totalCustomerMessages + totalPageReplies,
                totalCustomerMessages,
                totalPageReplies,
                msgPositive,
                msgNeutral,
                msgWarning,
                msgCritical,
                messageNegativeRate
        );

        for (FacebookPost post : posts) {
            String snippet = createSnippet(post.message());
            postReactions.add(new PostReactionAnalysis(
                    post.id(),
                    snippet,
                    post.createdTime(),
                    post.reactions(),
                    post.userReactions()
            ));

            for (FacebookComment comment : post.comments()) {
                comments.add(analyzeComment(comment, post.id(), snippet));
            }
        }

        for (FacebookReview review : fetchedReviews) {
            reviews.add(new AnalyzedReview(review, analyzeReview(review)));
        }

        comments.sort(Comparator.comparing(CommentAnalysis::createdTime).reversed());
        postReactions.sort(Comparator.comparing(PostReactionAnalysis::createdTime).reversed());
        reviews.sort(Comparator.comparing(review -> review.review().createdTime(), Comparator.reverseOrder()));

        int positive = countByLevel(comments, SentimentLevel.POSITIVE);
        int neutral = countByLevel(comments, SentimentLevel.NEUTRAL);
        int warning = countByLevel(comments, SentimentLevel.WARNING_NEGATIVE);
        int critical = countByLevel(comments, SentimentLevel.CRITICAL_NEGATIVE);
        int negative = (int) comments.stream().filter(CommentAnalysis::flagged).count();
        double negativeRate = comments.isEmpty()
                ? 0.0
                : Math.round((negative * 1000.0) / comments.size()) / 10.0;
        int negativeReviews = (int) reviews.stream()
                .filter(review -> review.score().compound() <= config.negativeThreshold())
                .count();
        ReactionSummary reactionTotals = sumReactions(posts);
        ReactionSummary commentReactionTotals = sumCommentReactions(posts);
        int yesCount = (int) reviews.stream()
                .filter(r -> r.review() != null && r.review().isPositiveRecommendation())
                .count();
        int noCount = (int) reviews.stream()
                .filter(r -> r.review() != null && r.review().isNegativeRecommendation())
                .count();
        int totalReviews = reviews.size();

        int updatedRatingCount = initialRating.ratingCount() > 0
                ? Math.max(initialRating.ratingCount(), totalReviews)
                : (yesCount + noCount > 0 ? (yesCount + noCount) : totalReviews);

        double overallStarRating = initialRating.hasRatings() && initialRating.overallStarRating() > 0.0
                ? initialRating.overallStarRating()
                : (totalReviews > 0 ? Math.round(((double) yesCount / totalReviews) * 50.0) / 10.0 : 0.0);

        PageRatingSummary pageRating = new PageRatingSummary(
                overallStarRating,
                updatedRatingCount,
                yesCount,
                noCount
        );

        SyncResult result = new SyncResult(
                Instant.now(),
                config.negativeThreshold(),
                posts.size(),
                comments.size(),
                reactionTotals.total(),
                reactionTotals,
                commentReactionTotals.total(),
                commentReactionTotals,
                positive,
                neutral,
                warning,
                critical,
                negative,
                negativeRate,
                comments,
                postReactions,
                pageRating,
                reviews.size(),
                negativeReviews,
                reviews,
                messageSummary,
                conversations
        );

        dataExportService.export(result);
        return result;
    }

    private SentimentLevel prioritizeSentiment(SentimentLevel current, SentimentLevel candidate) {
        if (current == null) {
            return candidate;
        }
        if (current == SentimentLevel.CRITICAL_NEGATIVE || candidate == SentimentLevel.CRITICAL_NEGATIVE) {
            return SentimentLevel.CRITICAL_NEGATIVE;
        }
        if (current == SentimentLevel.WARNING_NEGATIVE || candidate == SentimentLevel.WARNING_NEGATIVE) {
            return SentimentLevel.WARNING_NEGATIVE;
        }
        if (current == SentimentLevel.POSITIVE || candidate == SentimentLevel.POSITIVE) {
            return SentimentLevel.POSITIVE;
        }
        return SentimentLevel.NEUTRAL;
    }

    private SentimentScore analyzeReview(FacebookReview review) {
        String text = review.reviewText() == null ? "" : review.reviewText();
        if (!text.isBlank()) {
            return analyzer.analyze(text);
        }
        if (review.isPositiveRecommendation()) {
            return new SentimentScore(0.5, 0.5, 0.5, 0.0, SentimentLevel.POSITIVE);
        }
        if (review.isNegativeRecommendation()) {
            return new SentimentScore(-0.5, 0.0, 0.5, 0.5, SentimentLevel.CRITICAL_NEGATIVE);
        }
        return new SentimentScore(0.0, 0.0, 1.0, 0.0, SentimentLevel.NEUTRAL);
    }

    private ReactionSummary sumReactions(List<FacebookPost> posts) {
        return sumReactionSummaries(posts.stream().map(FacebookPost::reactions).toList());
    }

    private ReactionSummary sumCommentReactions(List<FacebookPost> posts) {
        List<ReactionSummary> summaries = new ArrayList<>();
        for (FacebookPost post : posts) {
            for (FacebookComment comment : post.comments()) {
                summaries.add(comment.reactions());
                for (FacebookComment reply : comment.replies()) {
                    summaries.add(reply.reactions());
                }
            }
        }
        return sumReactionSummaries(summaries);
    }

    private CommentAnalysis analyzeComment(FacebookComment comment, String postId, String postSnippet) {
        SentimentScore score = analyzer.analyze(comment.message());
        List<CommentAnalysis> replies = new ArrayList<>();
        for (FacebookComment reply : comment.replies()) {
            replies.add(analyzeComment(reply, postId, postSnippet));
        }
        return new CommentAnalysis(
                comment.id(),
                postId,
                postSnippet,
                comment.message(),
                comment.createdTime(),
                score.compound(),
                score.level(),
                score.compound() <= config.negativeThreshold(),
                comment.reactions(),
                comment.from(),
                replies,
                comment.userReactions()
        );
    }

    private ReactionSummary sumReactionSummaries(List<ReactionSummary> reactions) {
        return new ReactionSummary(
                reactions.stream().mapToInt(ReactionSummary::total).sum(),
                reactions.stream().mapToInt(ReactionSummary::like).sum(),
                reactions.stream().mapToInt(ReactionSummary::love).sum(),
                reactions.stream().mapToInt(ReactionSummary::care).sum(),
                reactions.stream().mapToInt(ReactionSummary::haha).sum(),
                reactions.stream().mapToInt(ReactionSummary::wow).sum(),
                reactions.stream().mapToInt(ReactionSummary::sad).sum(),
                reactions.stream().mapToInt(ReactionSummary::angry).sum()
        );
    }

    private int countByLevel(List<CommentAnalysis> comments, SentimentLevel level) {
        return (int) comments.stream().filter(comment -> comment.level() == level).count();
    }

    private String createSnippet(String message) {
        if (message == null || message.isBlank()) {
            return "[No post text]";
        }
        return message.length() > 90 ? message.substring(0, 87) + "..." : message;
    }
}
