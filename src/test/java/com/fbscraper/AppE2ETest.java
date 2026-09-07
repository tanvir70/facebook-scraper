package com.fbscraper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppE2ETest {

    @Test
    void shouldExecuteMainPipelineAndGenerateDashboard() {
        // Run main with explicit offline test mode
        App.main(new String[]{"--offline"});

        Path dashboard = Path.of("output/dashboard.html");
        Path jsonPath = Path.of("output/comments.json");

        assertThat(Files.exists(dashboard)).isTrue();
        assertThat(Files.exists(jsonPath)).isTrue();

        try {
            String content = Files.readString(dashboard);
            assertThat(content).contains("Facebook Scraper Dashboard");
            assertThat(content).contains("This update is HORRIBLE!");
            assertThat(content).contains("Worst customer support ever");
            assertThat(content).contains("CRITICAL");

            String json = Files.readString(jsonPath);
            assertThat(json).contains("This update is HORRIBLE!");
            assertThat(json).contains("compound");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
