package com.fbscraper.model.instagram;

import java.time.Instant;

public record InstagramMediaAnalysis(
        String mediaId,
        String mediaSnippet,
        String mediaType,
        String permalink,
        Instant timestamp,
        int likeCount,
        int commentsCount
) {
    public InstagramMediaAnalysis {
        mediaSnippet = mediaSnippet == null ? "" : mediaSnippet;
        mediaType = mediaType == null ? "IMAGE" : mediaType;
    }
}
