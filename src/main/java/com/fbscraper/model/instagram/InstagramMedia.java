package com.fbscraper.model.instagram;

import java.time.Instant;
import java.util.List;

public record InstagramMedia(
        String id,
        String caption,
        String mediaType,
        String mediaUrl,
        String permalink,
        Instant timestamp,
        int likeCount,
        int commentsCount,
        List<InstagramComment> comments
) {
    public InstagramMedia {
        caption = caption == null ? "" : caption;
        mediaType = mediaType == null ? "IMAGE" : mediaType;
        comments = comments == null ? List.of() : List.copyOf(comments);
    }

    public InstagramMedia(
            String id,
            String caption,
            Instant timestamp,
            int likeCount,
            int commentsCount,
            List<InstagramComment> comments
    ) {
        this(id, caption, "IMAGE", null, null, timestamp, likeCount, commentsCount, comments);
    }
}
