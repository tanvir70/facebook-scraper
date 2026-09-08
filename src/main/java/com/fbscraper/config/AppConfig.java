package com.fbscraper.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Immutable application configuration record for the Facebook Scraper.
 * <p>
 * Loaded strictly from a {@code config.properties} file without classpath or environment variable lookups.
 *
 * @param pageId            Facebook Page ID to scrape
 * @param accessToken       Facebook Page/User Access Token with required permissions
 * @param apiVersion        Facebook Graph API version (default "v26.0")
 * @param feedLimit         Number of posts to fetch per page (default 100, Meta max)
 * @param commentLimit      Number of comments to fetch per post (default 100, Meta max)
 * @param maxPages          Maximum number of feed pages to paginate through (default 5; 0 for unlimited)
 * @param negativeThreshold Compound score threshold below which comments are flagged as negative (default -0.05)
 */
public record AppConfig(
        String pageId,
        String accessToken,
        String apiVersion,
        int feedLimit,
        int commentLimit,
        int maxPages,
        double negativeThreshold
) {
    public static final String DEFAULT_API_VERSION = "v26.0";
    public static final int DEFAULT_FEED_LIMIT = 100;
    public static final int DEFAULT_COMMENT_LIMIT = 100;
    public static final int DEFAULT_MAX_PAGES = 5;
    public static final double DEFAULT_NEGATIVE_THRESHOLD = -0.05;

    /**
     * Loads configuration strictly from a {@code config.properties} file.
     * Looks in the current working directory, with a fallback to {@code src/main/resources/config.properties}.
     *
     * @return an initialized {@link AppConfig} instance
     * @throws IllegalStateException if the config.properties file cannot be found
     */
    public static AppConfig load() {
        Path rootConfig = Path.of("config.properties");
        Path resourceConfig = Path.of("src/main/resources/config.properties");
        Path configFile;

        if (Files.exists(rootConfig) && Files.exists(resourceConfig)) {
            try {
                var rootTime = Files.getLastModifiedTime(rootConfig).toInstant();
                var resourceTime = Files.getLastModifiedTime(resourceConfig).toInstant();
                configFile = resourceTime.isAfter(rootTime) ? resourceConfig : rootConfig;
            } catch (IOException e) {
                configFile = rootConfig;
            }
        } else if (Files.exists(rootConfig)) {
            configFile = rootConfig;
        } else {
            configFile = resourceConfig;
        }

        if (!Files.exists(configFile)) {
            throw new IllegalStateException(
                    "Configuration file not found! Expected 'config.properties' in working directory or 'src/main/resources/config.properties'. " +
                    "Please create it based on 'config.properties.template'."
            );
        }

        Properties props = new Properties();
        try (var in = new FileInputStream(configFile.toFile())) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config.properties from " + configFile.toAbsolutePath() + ": " + e.getMessage(), e);
        }

        return fromProperties(props);
    }

    /**
     * Constructs an {@link AppConfig} directly from a {@link Properties} object.
     *
     * @param props the loaded properties
     * @return a configured {@link AppConfig} instance
     */
    public static AppConfig fromProperties(Properties props) {
        String pageId = props.getProperty("fb.page.id", "").trim();
        String accessToken = props.getProperty("fb.access.token", "").trim();

        String apiVersion = props.getProperty("fb.api.version", DEFAULT_API_VERSION).trim();
        if (apiVersion.isEmpty()) {
            apiVersion = DEFAULT_API_VERSION;
        }

        int feedLimit = parseIntOrDefault(props.getProperty("fb.feed.limit"), DEFAULT_FEED_LIMIT);
        int commentLimit = parseIntOrDefault(props.getProperty("fb.comment.limit"), DEFAULT_COMMENT_LIMIT);
        int maxPages = parseIntOrDefault(props.getProperty("fb.max.pages"), DEFAULT_MAX_PAGES);
        double negativeThreshold = parseDoubleOrDefault(props.getProperty("app.negative.threshold"), DEFAULT_NEGATIVE_THRESHOLD);

        return new AppConfig(pageId, accessToken, apiVersion, feedLimit, commentLimit, maxPages, negativeThreshold);
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
