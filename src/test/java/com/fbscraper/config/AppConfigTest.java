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
        assertThat(config.conversationLimit()).isEqualTo(100);
        assertThat(config.messageLimit()).isEqualTo(100);
        assertThat(config.reactionLimit()).isEqualTo(100);
        assertThat(config.nestedCommentLimit()).isEqualTo(100);
        assertThat(config.igAccountId()).isEmpty();
        assertThat(config.hasInstagramAccountId()).isFalse();
    }

    @Test
    void shouldParseExplicitProperties() {
        Properties props = new Properties();
        props.setProperty("fb.page.id", "123456789");
        props.setProperty("fb.access.token", "EAABsampletoken");
        props.setProperty("fb.api.version", "v26.0");
        props.setProperty("fb.feed.limit", "50");
        props.setProperty("fb.comment.limit", "75");
        props.setProperty("fb.conversation.limit", "40");
        props.setProperty("fb.message.limit", "60");
        props.setProperty("fb.reaction.limit", "30");
        props.setProperty("fb.nested_comment.limit", "25");
        props.setProperty("fb.max.pages", "10");
        props.setProperty("app.negative.threshold", "-0.15");
        props.setProperty("fb.ig-account-id", "178414000123");

        AppConfig config = AppConfig.fromProperties(props);

        assertThat(config.pageId()).isEqualTo("123456789");
        assertThat(config.accessToken()).isEqualTo("EAABsampletoken");
        assertThat(config.apiVersion()).isEqualTo("v26.0");
        assertThat(config.feedLimit()).isEqualTo(50);
        assertThat(config.commentLimit()).isEqualTo(75);
        assertThat(config.conversationLimit()).isEqualTo(40);
        assertThat(config.messageLimit()).isEqualTo(60);
        assertThat(config.reactionLimit()).isEqualTo(30);
        assertThat(config.nestedCommentLimit()).isEqualTo(25);
        assertThat(config.maxPages()).isEqualTo(10);
        assertThat(config.negativeThreshold()).isEqualTo(-0.15);
        assertThat(config.igAccountId()).isEqualTo("178414000123");
        assertThat(config.hasInstagramAccountId()).isTrue();
    }
}
