package com.fbscraper.model;

import com.fbscraper.enums.SentimentLevel;

import java.time.Instant;
import java.util.List;

public record Conversation(
        String id,
        Instant updatedTime,
        List<User> participants,
        List<Message> messages,
        SentimentLevel overallSentiment,
        int customerMessageCount,
        int pageMessageCount
) {
    public Conversation(
            String id,
            Instant updatedTime,
            List<User> participants,
            List<Message> messages
    ) {
        this(id, updatedTime, participants, messages, SentimentLevel.NEUTRAL, 0, 0);
    }

    public Conversation {
        updatedTime = updatedTime == null ? Instant.now() : updatedTime;
        participants = participants == null ? List.of() : List.copyOf(participants);
        messages = messages == null ? List.of() : List.copyOf(messages);
        overallSentiment = overallSentiment == null ? SentimentLevel.NEUTRAL : overallSentiment;
    }
}
