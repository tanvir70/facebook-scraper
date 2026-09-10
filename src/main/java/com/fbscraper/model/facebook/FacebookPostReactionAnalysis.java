package com.fbscraper.model.facebook;

import java.time.Instant;
import java.util.List;

public record FacebookPostReactionAnalysis(
        String postId,
        String postSnippet,
        Instant createdTime,
        FacebookReactionSummary reactions,
        List<FacebookReaction> userReactions
) {
    public FacebookPostReactionAnalysis(String postId, String postSnippet, Instant createdTime, FacebookReactionSummary reactions) {
        this(postId, postSnippet, createdTime, reactions, List.of());
    }

    public FacebookPostReactionAnalysis {
        postSnippet = postSnippet == null ? "" : postSnippet;
        reactions = reactions == null ? FacebookReactionSummary.empty() : reactions;
        userReactions = userReactions == null ? List.of() : List.copyOf(userReactions);
    }
}
