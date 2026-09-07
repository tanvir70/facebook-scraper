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
        int positiveComments,
        int neutralComments,
        int warningComments,
        int criticalComments,
        int negativeComments,
        double negativeRate,
        List<CommentAnalysis> comments
) {
    public SyncResult {
        comments = comments == null ? List.of() : List.copyOf(comments);
    }
}
