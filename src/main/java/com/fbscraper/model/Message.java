package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record Message(
        String id,
        String message,
        Instant createdTime,
        User from,
        List<User> to,
        List<Attachment> attachments,
        boolean isFromPage,
        SentimentScore score,
        boolean flagged
) {
    public Message(
            String id,
            String message,
            Instant createdTime,
            User from,
            List<User> to,
            List<Attachment> attachments
    ) {
        this(id, message, createdTime, from, to, attachments, false, null, false);
    }

    public Message(
            String id,
            String message,
            Instant createdTime,
            User from,
            List<User> to
    ) {
        this(id, message, createdTime, from, to, List.of(), false, null, false);
    }

    public Message {
        message = message == null ? "" : message;
        createdTime = createdTime == null ? Instant.now() : createdTime;
        to = to == null ? List.of() : List.copyOf(to);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
