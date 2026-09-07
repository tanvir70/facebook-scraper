package com.fbscraper.web;

import com.fbscraper.config.AppConfig;
import com.fbscraper.service.SentimentSyncService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalWebServerTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldServeDashboardAndRunSync() throws Exception {
        AppConfig config = new AppConfig("", "", "v26.0", true, -0.05);

        try (LocalWebServer server = new LocalWebServer(0, new SentimentSyncService(config))) {
            server.start();
            String baseUrl = "http://127.0.0.1:" + server.port();

            HttpResponse<String> dashboard = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            HttpResponse<String> sync = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/sync"))
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertThat(dashboard.statusCode()).isEqualTo(200);
            assertThat(dashboard.body()).contains("Sync now", "Customer sentiment");
            assertThat(sync.statusCode()).isEqualTo(200);
            assertThat(sync.body()).contains("\"totalPosts\":2", "\"totalComments\":8");
        }
    }
}
