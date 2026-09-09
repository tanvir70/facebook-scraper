package com.fbscraper.model;

import com.fbscraper.enums.SentimentLevel;

import java.time.Instant;
import java.util.List;

public record AnalyzedConversation(
        String id,
        Instant updatedTime,
        List<FacebookParticipant> participants,
        List<AnalyzedMessage> messages,
        SentimentLevel overallSentiment,
        int customerMessageCount,
        int pageMessageCount
) {
    public AnalyzedConversation {
        updatedTime = updatedTime == null ? Instant.now() : updatedTime;
        participants = participants == null ? List.of() : List.copyOf(participants);
        messages = messages == null ? List.of() : List.copyOf(messages);
        overallSentiment = overallSentiment == null ? SentimentLevel.NEUTRAL : overallSentiment;
    }
}
