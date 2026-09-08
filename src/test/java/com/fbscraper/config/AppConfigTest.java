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
        assertThat(config.apiVersion()).isEqualTo("v26.0");
        assertThat(config.feedLimit()).isEqualTo(100);
        assertThat(config.commentLimit()).isEqualTo(100);
        assertThat(config.maxPages()).isEqualTo(5);
        assertThat(config.negativeThreshold()).isEqualTo(-0.05);
    }

    @Test
    void shouldParseExplicitProperties() {
        Properties props = new Properties();
        props.setProperty("fb.page.id", "123456789");
        props.setProperty("fb.access.token", "EAABsampletoken");
        props.setProperty("fb.api.version", "v26.0");
        props.setProperty("fb.feed.limit", "50");
        props.setProperty("fb.comment.limit", "75");
        props.setProperty("fb.max.pages", "10");
        props.setProperty("app.negative.threshold", "-0.15");

        AppConfig config = AppConfig.fromProperties(props);

        assertThat(config.pageId()).isEqualTo("123456789");
        assertThat(config.accessToken()).isEqualTo("EAABsampletoken");
        assertThat(config.apiVersion()).isEqualTo("v26.0");
        assertThat(config.feedLimit()).isEqualTo(50);
        assertThat(config.commentLimit()).isEqualTo(75);
        assertThat(config.maxPages()).isEqualTo(10);
        assertThat(config.negativeThreshold()).isEqualTo(-0.15);
    }
}
