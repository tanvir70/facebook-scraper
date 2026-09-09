package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record FacebookMessage(
        String id,
        String message,
        Instant createdTime,
        FacebookUser from,
        List<FacebookUser> to,
        List<FacebookAttachment> attachments,
        boolean isFromPage,
        SentimentScore score,
        boolean flagged
) {
    public FacebookMessage(
            String id,
            String message,
            Instant createdTime,
            FacebookUser from,
            List<FacebookUser> to,
            List<FacebookAttachment> attachments
    ) {
        this(id, message, createdTime, from, to, attachments, false, null, false);
    }

    public FacebookMessage(
            String id,
            String message,
            Instant createdTime,
            FacebookUser from,
            List<FacebookUser> to
    ) {
        this(id, message, createdTime, from, to, List.of(), false, null, false);
    }

    public FacebookMessage {
        message = message == null ? "" : message;
        createdTime = createdTime == null ? Instant.now() : createdTime;
        to = to == null ? List.of() : List.copyOf(to);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
