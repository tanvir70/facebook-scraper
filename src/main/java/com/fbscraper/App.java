package com.fbscraper;

import com.fbscraper.config.AppConfig;
import com.fbscraper.service.SentimentSyncService;
import com.fbscraper.web.LocalWebServer;

/**
 * Starts the local Facebook monitoring dashboard.
 */
public class App {

    public static void main(String[] args) {
        AppConfig config;
        try {
            config = AppConfig.load();
        } catch (RuntimeException e) {
            System.err.println("[App] Could not load configuration: " + e.getMessage());
            return;
        }

        int port = parsePort(args);
        SentimentSyncService syncService = new SentimentSyncService(config);
        LocalWebServer server = new LocalWebServer(port, syncService);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();

        System.out.println("==================================================");
        System.out.println(" Facebook Social Monitor");
        System.out.println(" Page ID       : " + config.pageId());
        System.out.println(" Feed pages    : " + (config.maxPages() <= 0 ? "Unlimited" : config.maxPages()));
        System.out.println(" Posts/page    : " + config.feedLimit());
        System.out.println(" Comments/post : " + config.commentLimit());
        System.out.println(" Previous data : " + (server.latestResult().isPresent() ? "Loaded (" + server.latestResult().get().comments().size() + " comments, " + server.latestResult().get().reviews().size() + " reviews)" : "None"));
        System.out.println(" Dashboard     : http://localhost:" + server.port());
        System.out.println(" Press Ctrl+C to stop");
        System.out.println("==================================================");
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
