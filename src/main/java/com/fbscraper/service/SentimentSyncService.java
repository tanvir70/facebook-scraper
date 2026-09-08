package com.fbscraper.service;

import com.fbscraper.client.FacebookClient;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.CommentAnalysis;
import com.fbscraper.model.AnalyzedReview;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.FacebookReview;
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

/**
 * Runs one complete synchronization: fetch Page comments and reactions, then analyze the comments.
 */
public final class SentimentSyncService {

    private final FacebookClient facebookClient;
    private final VaderAnalyzer analyzer;
    private final AppConfig config;

    public SentimentSyncService(AppConfig config) {
        this(config, new FacebookClient(config), VaderAnalyzer.createDefault());
    }

    public SentimentSyncService(AppConfig config, FacebookClient facebookClient, VaderAnalyzer analyzer) {
        this.config = config;
        this.facebookClient = facebookClient;
        this.analyzer = analyzer;
    }

    public SyncResult sync() {
        List<FacebookPost> posts = facebookClient.fetchPageFeed();
        PageRatingSummary pageRating = facebookClient.fetchPageRatingSummary();
        List<FacebookReview> fetchedReviews = facebookClient.fetchPageReviews();
        List<CommentAnalysis> comments = new ArrayList<>();
        List<PostReactionAnalysis> postReactions = new ArrayList<>();
        List<AnalyzedReview> reviews = new ArrayList<>();

        for (FacebookPost post : posts) {
            String snippet = createSnippet(post.message());
            postReactions.add(new PostReactionAnalysis(
                    post.id(),
                    snippet,
                    post.createdTime(),
                    post.reactions()
            ));

            for (FacebookComment comment : post.comments()) {
                SentimentScore score = analyzer.analyze(comment.message());
                comments.add(new CommentAnalysis(
                        comment.id(),
                        post.id(),
                        snippet,
                        comment.message(),
                        comment.createdTime(),
                        score.compound(),
                        score.level(),
                        score.compound() <= config.negativeThreshold(),
                        comment.reactions()
                ));
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
        int negativeReviews = (int) reviews.stream()
                .filter(review -> review.score().compound() <= config.negativeThreshold())
                .count();
        ReactionSummary reactionTotals = sumReactions(posts);
        ReactionSummary commentReactionTotals = sumCommentReactions(posts);
        double negativeRate = comments.isEmpty()
                ? 0.0
                : Math.round((negative * 1000.0) / comments.size()) / 10.0;

        return new SyncResult(
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
                reviews
        );
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
        return sumReactionSummaries(posts.stream()
                .flatMap(post -> post.comments().stream())
                .map(FacebookComment::reactions)
                .toList());
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
