package com.fbscraper.model;

import java.time.Instant;

/**
 * UI-friendly reaction totals for one Facebook Page post.
 */
public record PostReactionAnalysis(
        String postId,
        String postSnippet,
        Instant createdTime,
        ReactionSummary reactions
) {}
