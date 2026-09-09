package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record SyncResult(
        Instant syncedAt,
        double negativeThreshold,
        int totalPosts,
        int totalComments,
        int totalReactions,
        ReactionSummary reactionTotals,
        int totalCommentReactions,
        ReactionSummary commentReactionTotals,
        int positiveComments,
        int neutralComments,
        int warningComments,
        int criticalComments,
        int negativeComments,
        double negativeRate,
        List<CommentAnalysis> comments,
        List<PostReactionAnalysis> postReactions,
        PageRatingSummary pageRating,
        int totalReviews,
        int negativeReviews,
        List<FacebookReview> reviews,
        MessageSentimentSummary messageSummary,
        List<FacebookConversation> conversations
) {
    public SyncResult(
            Instant syncedAt,
            double negativeThreshold,
            int totalPosts,
            int totalComments,
            int totalReactions,
            ReactionSummary reactionTotals,
            int totalCommentReactions,
            ReactionSummary commentReactionTotals,
            int positiveComments,
            int neutralComments,
            int warningComments,
            int criticalComments,
            int negativeComments,
            double negativeRate,
            List<CommentAnalysis> comments,
            List<PostReactionAnalysis> postReactions,
            PageRatingSummary pageRating,
            int totalReviews,
            int negativeReviews,
            List<FacebookReview> reviews
    ) {
        this(
                syncedAt,
                negativeThreshold,
                totalPosts,
                totalComments,
                totalReactions,
                reactionTotals,
                totalCommentReactions,
                commentReactionTotals,
                positiveComments,
                neutralComments,
                warningComments,
                criticalComments,
                negativeComments,
                negativeRate,
                comments,
                postReactions,
                pageRating,
                totalReviews,
                negativeReviews,
                reviews,
                MessageSentimentSummary.EMPTY,
                List.of()
        );
    }

    public SyncResult {
        reactionTotals = reactionTotals == null ? ReactionSummary.empty() : reactionTotals;
        commentReactionTotals = commentReactionTotals == null
                ? ReactionSummary.empty()
                : commentReactionTotals;
        comments = comments == null ? List.of() : List.copyOf(comments);
        postReactions = postReactions == null ? List.of() : List.copyOf(postReactions);
        pageRating = pageRating == null ? PageRatingSummary.EMPTY : pageRating;
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
        messageSummary = messageSummary == null ? MessageSentimentSummary.EMPTY : messageSummary;
        conversations = conversations == null ? List.of() : List.copyOf(conversations);
    }
}
