package com.fbscraper.model.facebook;

import com.fbscraper.model.MessageSentimentSummary;

import java.time.Instant;
import java.util.List;

public record FacebookSyncResult(
        Instant syncedAt,
        double negativeThreshold,
        int totalPosts,
        int totalComments,
        int totalReactions,
        FacebookReactionSummary reactionTotals,
        int totalCommentReactions,
        FacebookReactionSummary commentReactionTotals,
        int positiveComments,
        int neutralComments,
        int warningComments,
        int criticalComments,
        int negativeComments,
        double negativeRate,
        List<FacebookCommentAnalysis> comments,
        List<FacebookPostReactionAnalysis> postReactions,
        FacebookPageRatingSummary pageRating,
        int totalReviews,
        int negativeReviews,
        List<FacebookReview> reviews,
        MessageSentimentSummary messageSummary,
        List<FacebookConversation> conversations
) {
    public FacebookSyncResult {
        reactionTotals = reactionTotals == null ? FacebookReactionSummary.empty() : reactionTotals;
        commentReactionTotals = commentReactionTotals == null ? FacebookReactionSummary.empty() : commentReactionTotals;
        comments = comments == null ? List.of() : List.copyOf(comments);
        postReactions = postReactions == null ? List.of() : List.copyOf(postReactions);
        pageRating = pageRating == null ? FacebookPageRatingSummary.EMPTY : pageRating;
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
        messageSummary = messageSummary == null ? MessageSentimentSummary.EMPTY : messageSummary;
        conversations = conversations == null ? List.of() : List.copyOf(conversations);
    }
}
