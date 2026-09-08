package com.fbscraper.client;

import com.fbscraper.config.AppConfig;
import com.fbscraper.model.FacebookPost;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FacebookClientTest {

    @Test
    void shouldLoadAndParseSampleFeedInOfflineMode() {
        AppConfig config = new AppConfig("", "", "v26.0", true, -0.05);
        FacebookClient client = new FacebookClient(config, Path.of("data/sample_feed.json"));

        List<FacebookPost> posts = client.fetchPageFeed();

        assertThat(posts).hasSize(2);

        FacebookPost firstPost = posts.get(0);
        assertThat(firstPost.id()).isEqualTo("10001_20001");
        assertThat(firstPost.message()).contains("Excited to announce our new summer release");
        assertThat(firstPost.comments()).hasSize(5);

        // Check first comment
        assertThat(firstPost.comments().get(0).id()).isEqualTo("comm_1");
        assertThat(firstPost.comments().get(0).message()).contains("Works like a charm");
        assertThat(firstPost.reactions().total()).isEqualTo(15);
        assertThat(firstPost.reactions().angry()).isEqualTo(2);

        // Check second post
        FacebookPost secondPost = posts.get(1);
        assertThat(secondPost.id()).isEqualTo("10001_20002");
        assertThat(secondPost.comments()).hasSize(3);
    }

    @Test
    void shouldParseRawJsonStringDirectly() {
        String json = """
        {
          "data": [
            {
              "id": "post_1",
              "message": "Single post test",
              "created_time": "2026-07-01T12:00:00+0000",
              "reactions": {"summary": {"total_count": 9}},
              "like": {"summary": {"total_count": 4}},
              "love": {"summary": {"total_count": 2}},
              "care": {"summary": {"total_count": 1}},
              "haha": {"summary": {"total_count": 0}},
              "wow": {"summary": {"total_count": 1}},
              "sad": {"summary": {"total_count": 0}},
              "angry": {"summary": {"total_count": 1}},
              "comments": {
                "data": [
                  {
                    "id": "c_1",
                    "message": "Nice test!",
                    "created_time": "2026-07-01T12:05:00+0000"
                  }
                ]
              }
            }
          ]
        }
        """;

        FacebookClient client = new FacebookClient(new AppConfig("", "", "v26.0", true, -0.05));
        List<FacebookPost> posts = client.parseFeedJson(json);

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).id()).isEqualTo("post_1");
        assertThat(posts.get(0).comments()).hasSize(1);
        assertThat(posts.get(0).comments().get(0).message()).isEqualTo("Nice test!");
        assertThat(posts.get(0).reactions().total()).isEqualTo(9);
        assertThat(posts.get(0).reactions().like()).isEqualTo(4);
        assertThat(posts.get(0).reactions().love()).isEqualTo(2);
    }

    @Test
    void shouldBuildValidEncodedFeedUrl() {
        AppConfig config = new AppConfig("1214847765056124", "EAABsample", "v26.0", false, -0.05);
        FacebookClient client = new FacebookClient(config);

        String url = client.buildFeedUrl();

        // Must not throw IllegalArgumentException when creating URI
        java.net.URI uri = java.net.URI.create(url);
        assertThat(uri).isNotNull();
        assertThat(url).contains("https://graph.facebook.com/v26.0/1214847765056124/feed?");
        assertThat(url).contains("%7B");
        assertThat(url).contains("%7D");
        assertThat(java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8))
                .contains("reactions.limit(0).summary(total_count)")
                .contains("reactions.type(ANGRY).limit(0).summary(total_count).as(angry)");
    }
}
