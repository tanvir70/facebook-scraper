package com.fbscraper.model.instagram;

import com.fbscraper.enums.SentimentLevel;

import java.time.Instant;
import java.util.List;

public record InstagramConversation(
        String id,
        Instant updatedTime,
        List<InstagramUser> participants,
        List<InstagramMessage> messages,
        SentimentLevel overallSentiment,
        int customerMessageCount,
        int businessMessageCount
) {
    public InstagramConversation(
            String id,
            Instant updatedTime,
            List<InstagramUser> participants,
            List<InstagramMessage> messages
    ) {
        this(id, updatedTime, participants, messages, SentimentLevel.NEUTRAL, 0, 0);
    }

    public InstagramConversation {
        updatedTime = updatedTime == null ? Instant.now() : updatedTime;
        participants = participants == null ? List.of() : List.copyOf(participants);
        messages = messages == null ? List.of() : List.copyOf(messages);
        overallSentiment = overallSentiment == null ? SentimentLevel.NEUTRAL : overallSentiment;
    }
}
