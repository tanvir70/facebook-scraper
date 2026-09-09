package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

/**
 * UI-friendly reaction totals and reactors for one Facebook Page post.
 */
public record PostReactionAnalysis(
        String postId,
        String postSnippet,
        Instant createdTime,
        ReactionSummary reactions,
        List<FacebookReaction> userReactions
) {
    public PostReactionAnalysis {
        reactions = reactions == null ? ReactionSummary.empty() : reactions;
        userReactions = userReactions == null ? List.of() : List.copyOf(userReactions);
    }

    public PostReactionAnalysis(
            String postId,
            String postSnippet,
            Instant createdTime,
            ReactionSummary reactions
    ) {
        this(postId, postSnippet, createdTime, reactions, List.of());
    }
}
