package com.fbscraper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Properties;

@ConfigurationProperties(prefix = "fb")
public record AppConfig(
        String pageId,
        String accessToken,
        @DefaultValue("v26.0") String apiVersion,
        @DefaultValue("100") int feedLimit,
        @DefaultValue("100") int commentLimit,
        @DefaultValue("100") int conversationLimit,
        @DefaultValue("100") int messageLimit,
        @DefaultValue("100") int reactionLimit,
        @DefaultValue("100") int nestedCommentLimit,
        @DefaultValue("5") int maxPages,
        @DefaultValue("-0.05") double negativeThreshold
) {
    public static final String DEFAULT_API_VERSION = "v26.0";
    public static final int DEFAULT_FEED_LIMIT = 100;
    public static final int DEFAULT_COMMENT_LIMIT = 100;
    public static final int DEFAULT_CONVERSATION_LIMIT = 100;
    public static final int DEFAULT_MESSAGE_LIMIT = 100;
    public static final int DEFAULT_REACTION_LIMIT = 100;
    public static final int DEFAULT_NESTED_COMMENT_LIMIT = 100;
    public static final int DEFAULT_MAX_PAGES = 5;
    public static final double DEFAULT_NEGATIVE_THRESHOLD = -0.05;

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public AppConfig {
    }

    public AppConfig(
            String pageId,
            String accessToken,
            String apiVersion,
            int feedLimit,
            int commentLimit,
            int maxPages,
            double negativeThreshold
    ) {
        this(pageId, accessToken, apiVersion, feedLimit, commentLimit, DEFAULT_CONVERSATION_LIMIT, DEFAULT_MESSAGE_LIMIT, DEFAULT_REACTION_LIMIT, DEFAULT_NESTED_COMMENT_LIMIT, maxPages, negativeThreshold);
    }

    public static AppConfig fromProperties(Properties props) {
        String pageId = props.getProperty("fb.page.id", props.getProperty("fb.page-id", "")).trim();
        String accessToken = props.getProperty("fb.access.token", props.getProperty("fb.access-token", "")).trim();

        String apiVersion = props.getProperty("fb.api.version", props.getProperty("fb.api-version", DEFAULT_API_VERSION)).trim();
        if (apiVersion.isEmpty()) {
            apiVersion = DEFAULT_API_VERSION;
        }

        int feedLimit = parseIntOrDefault(props.getProperty("fb.feed.limit", props.getProperty("fb.feed-limit")), DEFAULT_FEED_LIMIT);
        int commentLimit = parseIntOrDefault(props.getProperty("fb.comment.limit", props.getProperty("fb.comment-limit")), DEFAULT_COMMENT_LIMIT);
        int conversationLimit = parseIntOrDefault(props.getProperty("fb.conversation.limit", props.getProperty("fb.conversation-limit")), DEFAULT_CONVERSATION_LIMIT);
        int messageLimit = parseIntOrDefault(props.getProperty("fb.message.limit", props.getProperty("fb.message-limit")), DEFAULT_MESSAGE_LIMIT);
        int reactionLimit = parseIntOrDefault(props.getProperty("fb.reaction.limit", props.getProperty("fb.reaction-limit")), DEFAULT_REACTION_LIMIT);
        int nestedCommentLimit = parseIntOrDefault(props.getProperty("fb.nested_comment.limit", props.getProperty("fb.nested-comment-limit")), DEFAULT_NESTED_COMMENT_LIMIT);
        int maxPages = parseIntOrDefault(props.getProperty("fb.max.pages", props.getProperty("fb.max-pages")), DEFAULT_MAX_PAGES);
        double negativeThreshold = parseDoubleOrDefault(props.getProperty("app.negative.threshold", props.getProperty("fb.negative-threshold")), DEFAULT_NEGATIVE_THRESHOLD);

        return new AppConfig(pageId, accessToken, apiVersion, feedLimit, commentLimit, conversationLimit, messageLimit, reactionLimit, nestedCommentLimit, maxPages, negativeThreshold);
    }

    private static int parseIntOrDefault(String str, int defaultVal) {
        if (str == null || str.isBlank()) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static double parseDoubleOrDefault(String str, double defaultVal) {
        if (str == null || str.isBlank()) {
            return defaultVal;
        }
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
