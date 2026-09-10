package com.fbscraper.model.facebook;

import com.fbscraper.enums.SentimentLevel;

import java.time.Instant;
import java.util.List;

public record FacebookConversation(
        String id,
        Instant updatedTime,
        List<FacebookUser> participants,
        List<FacebookMessage> messages,
        SentimentLevel overallSentiment,
        int customerMessageCount,
        int pageMessageCount
) {
    public FacebookConversation(
            String id,
            Instant updatedTime,
            List<FacebookUser> participants,
            List<FacebookMessage> messages
    ) {
        this(id, updatedTime, participants, messages, SentimentLevel.NEUTRAL, 0, 0);
    }

    public FacebookConversation {
        updatedTime = updatedTime == null ? Instant.now() : updatedTime;
        participants = participants == null ? List.of() : List.copyOf(participants);
        messages = messages == null ? List.of() : List.copyOf(messages);
        overallSentiment = overallSentiment == null ? SentimentLevel.NEUTRAL : overallSentiment;
    }
}
