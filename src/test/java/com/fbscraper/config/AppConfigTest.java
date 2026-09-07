package com.fbscraper.config;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    @Test
    void shouldLoadDefaultConfigWhenPropertiesEmpty() {
        Properties props = new Properties();
        AppConfig config = AppConfig.fromProperties(props);

        assertThat(config.pageId()).isEmpty();
        assertThat(config.accessToken()).isEmpty();
        assertThat(config.apiVersion()).isEqualTo("v20.0");
        assertThat(config.offlineMode()).isTrue(); // Default to offline mode for safety
        assertThat(config.negativeThreshold()).isEqualTo(-0.05);
    }

    @Test
    void shouldParseExplicitProperties() {
        Properties props = new Properties();
        props.setProperty("fb.page.id", "123456789");
        props.setProperty("fb.access.token", "EAABsampletoken");
        props.setProperty("fb.api.version", "v19.0");
        props.setProperty("app.offline.mode", "false");
        props.setProperty("app.negative.threshold", "-0.15");

        AppConfig config = AppConfig.fromProperties(props);

        assertThat(config.pageId()).isEqualTo("123456789");
        assertThat(config.accessToken()).isEqualTo("EAABsampletoken");
        assertThat(config.apiVersion()).isEqualTo("v19.0");
        assertThat(config.offlineMode()).isFalse();
        assertThat(config.negativeThreshold()).isEqualTo(-0.15);
    }
}
