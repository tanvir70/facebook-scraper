package com.fbscraper.model.instagram;

import com.fbscraper.enums.SentimentLevel;

import java.time.Instant;
import java.util.List;

public record InstagramCommentAnalysis(
        String id,
        String mediaId,
        String mediaSnippet,
        String text,
        Instant timestamp,
        double score,
        SentimentLevel level,
        boolean flagged,
        int likeCount,
        InstagramUser from,
        List<InstagramCommentAnalysis> replies
) {
    public InstagramCommentAnalysis {
        mediaSnippet = mediaSnippet == null ? "" : mediaSnippet;
        from = from == null ? InstagramUser.ANONYMOUS : from;
        replies = replies == null ? List.of() : List.copyOf(replies);
    }
}
