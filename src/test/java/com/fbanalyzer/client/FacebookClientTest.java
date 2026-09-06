package com.fbanalyzer.client;

import com.fbanalyzer.config.AppConfig;
import com.fbanalyzer.model.FacebookPost;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FacebookClientTest {

    @Test
    void shouldLoadAndParseSampleFeedInOfflineMode() {
        AppConfig config = new AppConfig("", "", "v20.0", true, -0.05);
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

        FacebookClient client = new FacebookClient(new AppConfig("", "", "v20.0", true, -0.05));
        List<FacebookPost> posts = client.parseFeedJson(json);

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).id()).isEqualTo("post_1");
        assertThat(posts.get(0).comments()).hasSize(1);
        assertThat(posts.get(0).comments().get(0).message()).isEqualTo("Nice test!");
    }
}
