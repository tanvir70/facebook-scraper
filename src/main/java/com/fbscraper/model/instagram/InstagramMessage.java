package com.fbscraper.model.instagram;

import com.fbscraper.model.SentimentScore;

import java.time.Instant;
import java.util.List;

public record InstagramMessage(
        String id,
        String message,
        Instant createdTime,
        InstagramUser from,
        List<InstagramUser> to,
        List<InstagramAttachment> attachments,
        boolean isFromBusiness,
        SentimentScore score,
        boolean flagged
) {
    public InstagramMessage(
            String id,
            String message,
            Instant createdTime,
            InstagramUser from,
            List<InstagramUser> to,
            List<InstagramAttachment> attachments
    ) {
        this(id, message, createdTime, from, to, attachments, false, null, false);
    }

    public InstagramMessage {
        createdTime = createdTime == null ? Instant.now() : createdTime;
        to = to == null ? List.of() : List.copyOf(to);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
