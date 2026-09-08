package com.fbscraper.client;

import com.fbscraper.config.AppConfig;
import com.fbscraper.model.FacebookPost;
import com.fbscraper.model.FacebookReview;
import com.fbscraper.model.PageRatingSummary;
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
        assertThat(url).contains("access_token=EAABsample");

        String decodedUrl = URLDecoder.decode(url, StandardCharsets.UTF_8);
        assertThat(decodedUrl).contains("comments.limit(100){id,message,created_time}");
        assertThat(decodedUrl).contains("access_token=EAABsample");
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

    @Test
    void shouldFetchPageRatingSummarySuccessfully() {
        String json = """
        {
          "overall_star_rating": 4.8,
          "rating_count": 150,
          "id": "123"
        }
        """;

        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05);
        FacebookClient client = new FacebookClient(config, req -> new FakeResponse(200, json));

        PageRatingSummary summary = client.fetchPageRatingSummary();

        assertThat(summary.overallStarRating()).isEqualTo(4.8);
        assertThat(summary.ratingCount()).isEqualTo(150);
        assertThat(summary.hasRatings()).isTrue();
    }

    @Test
    void shouldReturnEmptyRatingSummaryOnError() {
        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05);
        FacebookClient client = new FacebookClient(config, req -> new FakeResponse(400, "{\"error\":{}}"));

        PageRatingSummary summary = client.fetchPageRatingSummary();
        assertThat(summary.hasRatings()).isFalse();
        assertThat(summary.overallStarRating()).isEqualTo(0.0);
    }

    @Test
    void shouldFetchPageReviewsWithPagination() {
        String page1Json = """
        {
          "data": [
            {
              "created_time": "2026-08-01T12:00:00+0000",
              "recommendation_type": "positive",
              "review_text": "Excellent service!",
              "rating": 5,
              "has_review": true
            }
          ],
          "paging": {
            "next": "https://graph.facebook.com/v26.0/123/ratings?after=next"
          }
        }
        """;

        String page2Json = """
        {
          "data": [
            {
              "created_time": "2026-07-20T10:00:00+0000",
              "recommendation_type": "negative",
              "review_text": "Did not arrive on time.",
              "rating": 2,
              "has_review": true
            }
          ],
          "paging": {}
        }
        """;

        Queue<HttpResponse<String>> queue = new LinkedList<>(List.of(
                new FakeResponse(200, page1Json),
                new FakeResponse(200, page2Json)
        ));

        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05);
        FacebookClient client = new FacebookClient(config, req -> queue.poll());

        List<FacebookReview> reviews = client.fetchPageReviews();

        assertThat(reviews).hasSize(2);
        assertThat(reviews.get(0).reviewText()).isEqualTo("Excellent service!");
        assertThat(reviews.get(0).isPositiveRecommendation()).isTrue();
        assertThat(reviews.get(1).reviewText()).isEqualTo("Did not arrive on time.");
        assertThat(reviews.get(1).isNegativeRecommendation()).isTrue();
    }

    @Test
    void shouldHandleExpiredAccessTokenWithHelpfulErrorMessage() {
        String errorJson = """
        {
          "error": {
            "message": "Error validating access token: Session has expired on Monday, 07-Sep-26 02:00:00 PDT.",
            "type": "OAuthException",
            "code": 190,
            "error_subcode": 463
          }
        }
        """;

        AppConfig config = new AppConfig("123", "expired_token", "v26.0", 100, 100, 5, -0.05);
        FacebookClient client = new FacebookClient(config, req -> new FakeResponse(401, errorJson));

        assertThatThrownBy(client::fetchPageFeed)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Facebook Access Token is expired or invalid")
                .hasMessageContaining("Session has expired");
    }
}
