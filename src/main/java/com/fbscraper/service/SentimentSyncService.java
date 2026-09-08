package com.fbscraper.service;

import com.fbscraper.client.FacebookClient;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.CommentAnalysis;
import com.fbscraper.model.FacebookComment;
import com.fbscraper.model.FacebookPost;
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
        List<CommentAnalysis> comments = new ArrayList<>();
        List<PostReactionAnalysis> postReactions = new ArrayList<>();

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
                        score.compound() <= config.negativeThreshold()
                ));
            }
        }

        comments.sort(Comparator.comparing(CommentAnalysis::createdTime).reversed());
        postReactions.sort(Comparator.comparing(PostReactionAnalysis::createdTime).reversed());

        int positive = countByLevel(comments, SentimentLevel.POSITIVE);
        int neutral = countByLevel(comments, SentimentLevel.NEUTRAL);
        int warning = countByLevel(comments, SentimentLevel.WARNING_NEGATIVE);
        int critical = countByLevel(comments, SentimentLevel.CRITICAL_NEGATIVE);
        int negative = (int) comments.stream().filter(CommentAnalysis::flagged).count();
        ReactionSummary reactionTotals = sumReactions(posts);
        double negativeRate = comments.isEmpty()
                ? 0.0
                : Math.round((negative * 1000.0) / comments.size()) / 10.0;

        return new SyncResult(
                Instant.now(),
                config.offlineMode(),
                config.negativeThreshold(),
                posts.size(),
                comments.size(),
                reactionTotals.total(),
                reactionTotals,
                positive,
                neutral,
                warning,
                critical,
                negative,
                negativeRate,
                comments,
                postReactions
        );
    }

    private ReactionSummary sumReactions(List<FacebookPost> posts) {
        return new ReactionSummary(
                posts.stream().mapToInt(post -> post.reactions().total()).sum(),
                posts.stream().mapToInt(post -> post.reactions().like()).sum(),
                posts.stream().mapToInt(post -> post.reactions().love()).sum(),
                posts.stream().mapToInt(post -> post.reactions().care()).sum(),
                posts.stream().mapToInt(post -> post.reactions().haha()).sum(),
                posts.stream().mapToInt(post -> post.reactions().wow()).sum(),
                posts.stream().mapToInt(post -> post.reactions().sad()).sum(),
                posts.stream().mapToInt(post -> post.reactions().angry()).sum()
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
