package com.fbscraper.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Immutable application configuration record.
 * <p>
 * Encapsulates Facebook Graph API credentials, versioning, mock offline mode,
 * and sentiment threshold thresholds.
 *
 * @param pageId            Facebook Page ID to scrape
 * @param accessToken       Facebook Page/User Access Token with required permissions
 * @param apiVersion        Facebook Graph API version (e.g. "v26.0")
 * @param offlineMode       true to use local mock data instead of live network calls
 * @param negativeThreshold Compound score threshold below which comments are flagged as negative
 */
public record AppConfig(
        String pageId,
        String accessToken,
        String apiVersion,
        boolean offlineMode,
        double negativeThreshold
) {
    public static final String DEFAULT_API_VERSION = "v26.0";
    public static final boolean DEFAULT_OFFLINE_MODE = true;
    public static final double DEFAULT_NEGATIVE_THRESHOLD = -0.05;

    /**
     * Loads configuration from local config.properties, classpath, or environment variables.
     *
     * @return an initialized {@link AppConfig} instance
     */
    public static AppConfig load() {
        Properties props = new Properties();

        // 1. Try to load config.properties from working directory
        Path localFile = Path.of("config.properties");
        if (Files.exists(localFile)) {
            try (InputStream in = new FileInputStream(localFile.toFile())) {
                props.load(in);
            } catch (IOException ignored) {
            }
        } else {
            // 2. Fall back to classpath config.properties if present
            try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (in != null) {
                    props.load(in);
                }
            } catch (IOException ignored) {
            }
        }

        return fromProperties(props);
    }

    /**
     * Resolves an {@link AppConfig} instance by reading from the supplied properties
     * with fallback to system environment variables and built-in defaults.
     *
     * @param props the loaded {@link Properties} object
     * @return a configured {@link AppConfig} instance
     */
    public static AppConfig fromProperties(Properties props) {
        String pageId = getPropOrEnv(props, "fb.page.id", "FB_PAGE_ID", "");
        String accessToken = getPropOrEnv(props, "fb.access.token", "FB_ACCESS_TOKEN", "");
        String apiVersion = getPropOrEnv(props, "fb.api.version", "FB_API_VERSION", DEFAULT_API_VERSION);

        String offlineStr = getPropOrEnv(props, "app.offline.mode", "APP_OFFLINE_MODE", String.valueOf(DEFAULT_OFFLINE_MODE));
        boolean offlineMode = Boolean.parseBoolean(offlineStr);

        String thresholdStr = getPropOrEnv(props, "app.negative.threshold", "APP_NEGATIVE_THRESHOLD", String.valueOf(DEFAULT_NEGATIVE_THRESHOLD));
        double negativeThreshold = parseDoubleOrDefault(thresholdStr, DEFAULT_NEGATIVE_THRESHOLD);

        return new AppConfig(pageId, accessToken, apiVersion, offlineMode, negativeThreshold);
    }

    private static String getPropOrEnv(Properties props, String propKey, String envKey, String defaultVal) {
        String val = props.getProperty(propKey);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        String env = System.getenv(envKey);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return defaultVal;
    }

    private static double parseDoubleOrDefault(String str, double defaultVal) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
