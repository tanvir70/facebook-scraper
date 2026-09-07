package com.fbscraper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppE2ETest {

    @Test
    void shouldExecuteMainPipelineAndGenerateDashboard() {
        // Run main with default offline mode
        App.main(new String[]{});

        Path dashboard = Path.of("output/dashboard.html");
        assertThat(Files.exists(dashboard)).isTrue();

        try {
            String content = Files.readString(dashboard);
            assertThat(content).contains("Facebook Scraper Dashboard");
            assertThat(content).contains("This update is HORRIBLE!");
            assertThat(content).contains("Worst customer support ever");
            assertThat(content).contains("CRITICAL");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
