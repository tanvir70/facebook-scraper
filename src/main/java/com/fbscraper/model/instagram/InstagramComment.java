package com.fbscraper.model.instagram;

import java.time.Instant;
import java.util.List;

public record InstagramComment(
        String id,
        String text,
        Instant timestamp,
        InstagramUser from,
        int likeCount,
        List<InstagramComment> replies
) {
    public InstagramComment {
        from = from == null ? InstagramUser.ANONYMOUS : from;
        replies = replies == null ? List.of() : List.copyOf(replies);
    }

    public InstagramComment(String id, String text, Instant timestamp) {
        this(id, text, timestamp, InstagramUser.ANONYMOUS, 0, List.of());
    }

    public InstagramComment(String id, String text, Instant timestamp, InstagramUser from, int likeCount) {
        this(id, text, timestamp, from, likeCount, List.of());
    }
}
