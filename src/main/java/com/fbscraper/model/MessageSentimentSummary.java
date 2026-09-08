package com.fbscraper.model;

/**
 * Aggregated metrics across all Page Messenger conversations.
 */
public record MessageSentimentSummary(
        int totalConversations,
        int totalMessages,
        int customerMessages,
        int pageReplies,
        int positiveMessages,
        int neutralMessages,
        int warningMessages,
        int criticalMessages,
        double negativeRate
) {
    public static final MessageSentimentSummary EMPTY =
            new MessageSentimentSummary(0, 0, 0, 0, 0, 0, 0, 0, 0.0);
}
