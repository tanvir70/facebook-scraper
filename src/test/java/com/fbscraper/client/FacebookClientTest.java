package com.fbscraper.client;

import com.fbscraper.config.AppConfig;
import com.fbscraper.model.FacebookPost;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacebookClientTest {

    record FakeResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (k, v) -> true); }
        @Override public URI uri() { return null; }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
    }

    @Test
    void shouldBuildValidEncodedFeedUrlWithConfiguredLimits() {
        AppConfig config = new AppConfig("1214847765056124", "EAABsample", "v26.0", 100, 100, 5, -0.05);
        FacebookClient client = new FacebookClient(config);

        String url = client.buildFeedUrl();

        // Must not throw IllegalArgumentException when creating URI
        URI uri = URI.create(url);
        assertThat(uri).isNotNull();
        assertThat(url).startsWith("https://graph.facebook.com/v26.0/1214847765056124/feed?");
        assertThat(url).contains("limit=100");
        assertThat(url).contains("%7B");
        assertThat(url).contains("%7D");

        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
        assertThat(decodedUrl).contains("comments.limit(100){id,message,created_time}");
    }

    @Test
    void shouldParseFeedPageWithNextCursor() {
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
          ],
          "paging": {
            "cursors": {
              "before": "curs_before",
              "after": "curs_after"
            },
            "next": "https://graph.facebook.com/v26.0/123/feed?after=curs_after"
          }
        }
        """;

        FacebookClient client = new FacebookClient(new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05));
        FacebookClient.FeedPage page = client.parseFeedPage(json);

        assertThat(page.posts()).hasSize(1);
        assertThat(page.posts().get(0).id()).isEqualTo("post_1");
        assertThat(page.posts().get(0).comments()).hasSize(1);
        assertThat(page.posts().get(0).comments().get(0).message()).isEqualTo("Nice test!");
        assertThat(page.nextUrl()).isEqualTo("https://graph.facebook.com/v26.0/123/feed?after=curs_after");
    }

    @Test
    void shouldPaginateAcrossMultiplePages() {
        String page1Json = """
        {
          "data": [
            {
              "id": "post_page_1",
              "message": "Post from page 1",
              "created_time": "2026-07-01T10:00:00+0000",
              "comments": { "data": [] }
            }
          ],
          "paging": {
            "next": "https://graph.facebook.com/v26.0/123/feed?page=2"
          }
        }
        """;

        String page2Json = """
        {
          "data": [
            {
              "id": "post_page_2",
              "message": "Post from page 2",
              "created_time": "2026-07-01T09:00:00+0000",
              "comments": { "data": [] }
            }
          ],
          "paging": {}
        }
        """;

        Queue<HttpResponse<String>> responses = new LinkedList<>(List.of(
                new FakeResponse(200, page1Json),
                new FakeResponse(200, page2Json)
        ));

        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05);
        FacebookClient client = new FacebookClient(config, req -> responses.poll());

        List<FacebookPost> allPosts = client.fetchPageFeed();

        assertThat(allPosts).hasSize(2);
        assertThat(allPosts.get(0).id()).isEqualTo("post_page_1");
        assertThat(allPosts.get(1).id()).isEqualTo("post_page_2");
    }

    @Test
    void shouldRespectMaxPagesLimit() {
        String pageJsonWithNext = """
        {
          "data": [
            {
              "id": "post_p",
              "message": "Post",
              "created_time": "2026-07-01T10:00:00+0000",
              "comments": { "data": [] }
            }
          ],
          "paging": {
            "next": "https://graph.facebook.com/v26.0/123/feed?after=next_page"
          }
        }
        """;

        // Set maxPages = 1
        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 1, -0.05);
        FacebookClient client = new FacebookClient(config, req -> new FakeResponse(200, pageJsonWithNext));

        List<FacebookPost> posts = client.fetchPageFeed();

        // Must stop after 1 page even though paging.next is present
        assertThat(posts).hasSize(1);
    }

    @Test
    void shouldThrowWhenCredentialsMissing() {
        AppConfig config = new AppConfig("", "", "v26.0", 100, 100, 5, -0.05);
        FacebookClient client = new FacebookClient(config);

        assertThatThrownBy(client::fetchPageFeed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required fb.page.id or fb.access.token");
    }
}
