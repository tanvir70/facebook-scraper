package com.fbscraper;

import com.fbscraper.config.AppConfig;
import com.fbscraper.service.SentimentSyncService;
import com.fbscraper.web.LocalWebServer;

/**
 * Starts the local Facebook sentiment dashboard.
 */
public class App {

    /**
     * Starts the dashboard. Pass {@code --offline} to force sample data and
     * {@code --port=9090} to use a different port.
     *
     * @param args command-line arguments (not currently required)
     */
    public static void main(String[] args) {
        AppConfig loadedConfig = AppConfig.load();
        if (hasArgument(args, "--offline")) {
            loadedConfig = new AppConfig(loadedConfig.pageId(), loadedConfig.accessToken(), loadedConfig.apiVersion(), true, loadedConfig.negativeThreshold());
        }
        int port = parsePort(args);
        SentimentSyncService syncService = new SentimentSyncService(loadedConfig);
        LocalWebServer server = new LocalWebServer(port, syncService);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();

        System.out.println("==================================================");
        System.out.println(" Facebook Sentiment Dashboard");
        System.out.println(" Mode : " + (loadedConfig.offlineMode() ? "OFFLINE / MOCK" : "LIVE FACEBOOK"));
        System.out.println(" URL  : http://localhost:" + server.port());
        System.out.println(" Press Ctrl+C to stop");
        System.out.println("==================================================");
    }

    private static boolean hasArgument(String[] args, String expected) {
        if (args == null) {
            return false;
        }
        for (String argument : args) {
            if (expected.equalsIgnoreCase(argument)) {
                return true;
            }
        }
        return false;
    }

    private static int parsePort(String[] args) {
        if (args != null) {
            for (String argument : args) {
                if (argument.startsWith("--port=")) {
                    try {
                        return Integer.parseInt(argument.substring("--port=".length()));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid port argument: " + argument, e);
                    }
                }
            }
        }
        return 8080;
    }
}
