package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents a single direct message within a conversation thread.
 */
public record FacebookMessage(
        String id,
        String message,
        Instant createdTime,
        FacebookParticipant from,
        List<FacebookParticipant> to
) {
    public FacebookMessage {
        message = message == null ? "" : message;
        createdTime = createdTime == null ? Instant.now() : createdTime;
        to = to == null ? List.of() : List.copyOf(to);
    }
}
