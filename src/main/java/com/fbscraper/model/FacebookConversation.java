package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record FacebookConversation(
        String id,
        Instant updatedTime,
        List<FacebookParticipant> participants,
        List<FacebookMessage> messages
) {
    public FacebookConversation {
        updatedTime = updatedTime == null ? Instant.now() : updatedTime;
        participants = participants == null ? List.of() : List.copyOf(participants);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
