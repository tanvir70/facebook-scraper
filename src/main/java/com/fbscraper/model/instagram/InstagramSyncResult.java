package com.fbscraper.model.instagram;

import com.fbscraper.model.MessageSentimentSummary;

import java.time.Instant;
import java.util.List;

public record InstagramSyncResult(
        Instant syncedAt,
        double negativeThreshold,
        int totalMedia,
        int totalComments,
        int totalLikes,
        int positiveComments,
        int neutralComments,
        int warningComments,
        int criticalComments,
        int negativeComments,
        double negativeRate,
        List<InstagramCommentAnalysis> comments,
        List<InstagramMediaAnalysis> media,
        MessageSentimentSummary messageSummary,
        List<InstagramConversation> conversations
) {
    public InstagramSyncResult {
        comments = comments == null ? List.of() : List.copyOf(comments);
        media = media == null ? List.of() : List.copyOf(media);
        messageSummary = messageSummary == null ? MessageSentimentSummary.EMPTY : messageSummary;
        conversations = conversations == null ? List.of() : List.copyOf(conversations);
    }
}
