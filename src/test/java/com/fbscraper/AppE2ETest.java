package com.fbscraper;

import com.fbscraper.client.FacebookClient;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.Comment;
import com.fbscraper.model.Conversation;
import com.fbscraper.model.Message;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.Post;
import com.fbscraper.model.Reaction;
import com.fbscraper.model.ReactionSummary;
import com.fbscraper.model.Review;
import com.fbscraper.model.SyncResult;
import com.fbscraper.model.User;
import com.fbscraper.sentiment.VaderAnalyzer;
import com.fbscraper.service.SentimentSyncService;
import com.fbscraper.service.DataExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppE2ETest {

    @Test
    void shouldRunTheDashboardSyncPipeline(@TempDir Path tempDir) {
        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05);
        Instant now = Instant.parse("2026-09-08T08:00:00Z");
        User commenter = new User("u10", "John Doe");
        User replier = new User("u11", "Replier Jane");
        Reaction commentReactor = new Reaction("u12", "Charlie", "LOVE");
        Reaction postReactor = new Reaction("u13", "Dave", "LIKE");
        User reviewer = new User("u14", "Reviewer Eve");

        Comment reply = new Comment("r1", "I disagree, it was okay", now, ReactionSummary.empty(), replier, List.of(), List.of());

        Comment comment = new Comment(
                "c1",
                "This service is horrible",
                now,
                new ReactionSummary(3, 1, 0, 1, 0, 0, 0, 1),
                commenter,
                List.of(reply),
                List.of(commentReactor)
        );
        Post post = new Post(
                "p1",
                "Support update",
                now,
                List.of(comment),
                new ReactionSummary(10, 4, 1, 1, 1, 0, 1, 2),
                List.of(postReactor)
        );
        Review review = new Review(now, "negative", "Very poor support", 2, true, reviewer);
        User customer = new User("u1", "Bob Customer");
        User page = new User("123", "Support Page");
        Message customerMsg = new Message("m1", "My order is terribly broken!", now, customer, List.of(page));
        Message pageMsg = new Message("m2", "We apologize for the inconvenience", now.plusSeconds(60), page, List.of(customer));
        Conversation conversation = new Conversation("t1", now.plusSeconds(60), List.of(customer, page), List.of(customerMsg, pageMsg));

        FacebookClient client = new FacebookClient(config, request -> {
            throw new AssertionError("The test client must not make HTTP calls");
        }) {
            @Override
            public List<Post> fetchPageFeed() {
                return List.of(post);
            }

            @Override
            public PageRatingSummary fetchPageRatingSummary() {
                return new PageRatingSummary(3.8, 42);
            }

            @Override
            public List<Review> fetchPageReviews() {
                return List.of(review);
            }

            @Override
            public List<Conversation> fetchPageConversations() {
                return List.of(conversation);
            }
        };

        SyncResult result = new SentimentSyncService(
                config,
                client,
                VaderAnalyzer.createDefault(),
                new DataExportService(tempDir)
        ).sync();

        assertThat(result.totalPosts()).isEqualTo(1);
        assertThat(result.totalComments()).isEqualTo(1);
        assertThat(result.totalReactions()).isEqualTo(10);
        assertThat(result.reactionTotals().care()).isEqualTo(1);
        assertThat(result.postReactions().get(0).userReactions()).hasSize(1);
        assertThat(result.postReactions().get(0).userReactions().get(0).name()).isEqualTo("Dave");

        assertThat(result.totalCommentReactions()).isEqualTo(3);
        var analyzedComment = result.comments().get(0);
        assertThat(analyzedComment.reactions().care()).isEqualTo(1);
        assertThat(analyzedComment.from().name()).isEqualTo("John Doe");
        assertThat(analyzedComment.userReactions()).hasSize(1);
        assertThat(analyzedComment.userReactions().get(0).name()).isEqualTo("Charlie");
        assertThat(analyzedComment.replies()).hasSize(1);
        assertThat(analyzedComment.replies().get(0).from().name()).isEqualTo("Replier Jane");

        assertThat(result.totalReviews()).isEqualTo(1);
        assertThat(result.negativeReviews()).isEqualTo(1);
        assertThat(result.reviews().get(0).reviewer().name()).isEqualTo("Reviewer Eve");
        assertThat(result.pageRating().overallStarRating()).isEqualTo(3.8);
        assertThat(result.messageSummary().totalConversations()).isEqualTo(1);
        assertThat(result.messageSummary().totalMessages()).isEqualTo(2);
        assertThat(result.messageSummary().customerMessages()).isEqualTo(1);
        assertThat(result.messageSummary().pageReplies()).isEqualTo(1);
        assertThat(result.messageSummary().negativeRate()).isEqualTo(100.0);
        assertThat(result.conversations()).hasSize(1);
        assertThat(result.conversations().get(0).messages().get(0).isFromPage()).isFalse();
        assertThat(result.conversations().get(0).messages().get(0).flagged()).isTrue();
        assertThat(result.conversations().get(0).messages().get(1).isFromPage()).isTrue();
    }
}
