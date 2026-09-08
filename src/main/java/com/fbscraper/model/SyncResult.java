package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

/**
 * Complete result of one Facebook fetch-and-analyze operation.
 */
public record SyncResult(
        Instant syncedAt,
        boolean offlineMode,
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
        List<PostReactionAnalysis> postReactions
) {
    public SyncResult {
        reactionTotals = reactionTotals == null ? ReactionSummary.empty() : reactionTotals;
        commentReactionTotals = commentReactionTotals == null
                ? ReactionSummary.empty()
                : commentReactionTotals;
        comments = comments == null ? List.of() : List.copyOf(comments);
        postReactions = postReactions == null ? List.of() : List.copyOf(postReactions);
    }
}
