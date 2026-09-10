package com.fbscraper.model.facebook;

import com.fbscraper.enums.SentimentLevel;

import java.time.Instant;
import java.util.List;

public record FacebookCommentAnalysis(
        String id,
        String postId,
        String postSnippet,
        String message,
        Instant createdTime,
        double score,
        SentimentLevel level,
        boolean flagged,
        FacebookReactionSummary reactions,
        FacebookUser from,
        List<FacebookCommentAnalysis> replies,
        List<FacebookReaction> userReactions
) {
    public FacebookCommentAnalysis {
        postSnippet = postSnippet == null ? "" : postSnippet;
        reactions = reactions == null ? FacebookReactionSummary.empty() : reactions;
        from = from == null ? FacebookUser.ANONYMOUS : from;
        replies = replies == null ? List.of() : List.copyOf(replies);
        userReactions = userReactions == null ? List.of() : List.copyOf(userReactions);
    }
}
