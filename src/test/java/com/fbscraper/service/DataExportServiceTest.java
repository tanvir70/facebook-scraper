package com.fbscraper.service;

import com.fbscraper.enums.SentimentLevel;
import com.fbscraper.model.MessageSentimentSummary;
import com.fbscraper.model.SentimentScore;
import com.fbscraper.model.facebook.FacebookCommentAnalysis;
import com.fbscraper.model.facebook.FacebookConversation;
import com.fbscraper.model.facebook.FacebookMessage;
import com.fbscraper.model.facebook.FacebookPageRatingSummary;
import com.fbscraper.model.facebook.FacebookPostReactionAnalysis;
import com.fbscraper.model.facebook.FacebookReaction;
import com.fbscraper.model.facebook.FacebookReactionSummary;
import com.fbscraper.model.facebook.FacebookReview;
import com.fbscraper.model.facebook.FacebookSyncResult;
import com.fbscraper.model.facebook.FacebookUser;
import com.fbscraper.model.instagram.InstagramCommentAnalysis;
import com.fbscraper.model.instagram.InstagramMediaAnalysis;
import com.fbscraper.model.instagram.InstagramSyncResult;
import com.fbscraper.model.instagram.InstagramUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DataExportServiceTest {

    @Test
    void shouldExportAndLoadMessagesJson(@TempDir Path tempDir) {
        DataExportService exportService = new DataExportService(tempDir);
        Instant now = Instant.parse("2026-09-08T12:00:00Z");

        FacebookUser customer = new FacebookUser("u1", "Alice");
        FacebookUser page = new FacebookUser("123", "Page");

        SentimentScore score1 = new SentimentScore(0.7, 0.4, 0.6, 0.0, SentimentLevel.POSITIVE);
        FacebookMessage msg1 = new FacebookMessage("m1", "I love this service!", now, customer, List.of(page), List.of(), false, score1, false);

        FacebookMessage msg2 = new FacebookMessage("m2", "Thank you Alice!", now.plusSeconds(30), page, List.of(customer), List.of(), true, null, false);

        FacebookConversation conv = new FacebookConversation(
                "t1",
                now.plusSeconds(30),
                List.of(customer, page),
                List.of(msg1, msg2),
                SentimentLevel.POSITIVE,
                1,
                1
        );

        MessageSentimentSummary summary = new MessageSentimentSummary(
                1, 2, 1, 1, 1, 0, 0, 0, 0.0
        );

        FacebookSyncResult result = new FacebookSyncResult(
                now,
                -0.05,
                0,
                0,
                0,
                FacebookReactionSummary.empty(),
                0,
                FacebookReactionSummary.empty(),
                0,
                0,
                0,
                0,
                0,
                0.0,
                List.of(),
                List.of(),
                FacebookPageRatingSummary.EMPTY,
                0,
                0,
                List.of(),
                summary,
                List.of(conv)
        );

        exportService.exportFacebook(result);

        Path messagesFile = tempDir.resolve("messages.json");
        assertThat(Files.exists(messagesFile)).isTrue();

        Optional<FacebookSyncResult> loaded = exportService.loadLatestFacebook(-0.05);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messageSummary().totalConversations()).isEqualTo(1);
        assertThat(loaded.get().messageSummary().totalMessages()).isEqualTo(2);
        assertThat(loaded.get().messageSummary().positiveMessages()).isEqualTo(1);
        assertThat(loaded.get().conversations()).hasSize(1);
        assertThat(loaded.get().conversations().get(0).messages()).hasSize(2);
        assertThat(loaded.get().conversations().get(0).messages().get(0).isFromPage()).isFalse();
        assertThat(loaded.get().conversations().get(0).messages().get(1).isFromPage()).isTrue();
    }

    @Test
    void shouldExportAndLoadCommentsReviewsAndPostReactionsWithUserDetails(@TempDir Path tempDir) {
        DataExportService exportService = new DataExportService(tempDir);
        Instant now = Instant.parse("2026-09-08T12:00:00Z");

        FacebookUser commenter = new FacebookUser("u10", "Commenter Sam");
        FacebookUser replier = new FacebookUser("u11", "Replier Sue");
        FacebookReaction commentReactor = new FacebookReaction("u12", "Reactor Ron", "HAHA");
        FacebookReaction postReactor = new FacebookReaction("u13", "Reactor Ray", "WOW");
        FacebookUser reviewer = new FacebookUser("u14", "Reviewer Rita");

        FacebookCommentAnalysis reply = new FacebookCommentAnalysis(
                "r1", "p1", "Snippet", "Replying to Sam", now, 0.2, SentimentLevel.POSITIVE, false,
                FacebookReactionSummary.empty(), replier, List.of(), List.of()
        );
        FacebookCommentAnalysis comment = new FacebookCommentAnalysis(
                "c1", "p1", "Snippet", "Great product", now, 0.6, SentimentLevel.POSITIVE, false,
                new FacebookReactionSummary(1, 0, 0, 0, 1, 0, 0, 0), commenter, List.of(reply), List.of(commentReactor)
        );

        FacebookPostReactionAnalysis postReaction = new FacebookPostReactionAnalysis(
                "p1", "Snippet", now, new FacebookReactionSummary(1, 0, 0, 0, 0, 1, 0, 0), List.of(postReactor)
        );

        FacebookReview review = new FacebookReview(
                now, "positive", "Loved it!", 5, true, reviewer,
                new SentimentScore(0.8, 0.6, 0.4, 0.0, SentimentLevel.POSITIVE)
        );

        FacebookSyncResult result = new FacebookSyncResult(
                now, -0.05, 1, 1, 1, new FacebookReactionSummary(1, 0, 0, 0, 0, 1, 0, 0),
                1, new FacebookReactionSummary(1, 0, 0, 0, 1, 0, 0, 0),
                1, 0, 0, 0, 0, 0.0,
                List.of(comment), List.of(postReaction), FacebookPageRatingSummary.EMPTY,
                1, 0, List.of(review), MessageSentimentSummary.EMPTY, List.of()
        );

        exportService.exportFacebook(result);

        Path commentsFile = tempDir.resolve("comments.json");
        Path postReactionsFile = tempDir.resolve("post_reactions.json");
        Path reviewsFile = tempDir.resolve("reviews.json");

        assertThat(Files.exists(commentsFile)).isTrue();
        assertThat(Files.exists(postReactionsFile)).isTrue();
        assertThat(Files.exists(reviewsFile)).isTrue();

        Optional<FacebookSyncResult> loaded = exportService.loadLatestFacebook(-0.05);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().comments()).hasSize(1);
        var loadedComment = loaded.get().comments().get(0);
        assertThat(loadedComment.from().name()).isEqualTo("Commenter Sam");
        assertThat(loadedComment.userReactions()).hasSize(1);
        assertThat(loadedComment.userReactions().get(0).type()).isEqualTo("HAHA");
        assertThat(loadedComment.replies()).hasSize(1);
        assertThat(loadedComment.replies().get(0).from().name()).isEqualTo("Replier Sue");

        assertThat(loaded.get().postReactions()).hasSize(1);
        assertThat(loaded.get().postReactions().get(0).userReactions()).hasSize(1);
        assertThat(loaded.get().postReactions().get(0).userReactions().get(0).name()).isEqualTo("Reactor Ray");

        assertThat(loaded.get().reviews()).hasSize(1);
        assertThat(loaded.get().reviews().get(0).reviewer().name()).isEqualTo("Reviewer Rita");
    }

    @Test
    void shouldExportAndLoadInstagramData(@TempDir Path tempDir) {
        DataExportService exportService = new DataExportService(tempDir);
        Instant now = Instant.parse("2026-09-08T12:00:00Z");

        InstagramUser igUser = new InstagramUser("ig_sam", "ig_sam");
        InstagramCommentAnalysis comment = new InstagramCommentAnalysis(
                "c_1", "media_1", "Snippet", "Great photo!", now, 0.6,
                SentimentLevel.POSITIVE, false, 10,
                igUser, List.of()
        );

        InstagramMediaAnalysis media = new InstagramMediaAnalysis(
                "media_1", "Snippet", "IMAGE", "https://instagram.com/p/1", now, 10, 1
        );

        InstagramSyncResult result = new InstagramSyncResult(
                now, -0.05, 1, 1, 10,
                1, 0, 0, 0, 0, 0.0,
                List.of(comment), List.of(media),
                MessageSentimentSummary.EMPTY, List.of()
        );

        exportService.exportInstagram(result);

        assertThat(Files.exists(tempDir.resolve("instagram_comments.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("instagram_media.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("instagram_sync-result.json"))).isTrue();

        Optional<InstagramSyncResult> loaded = exportService.loadLatestInstagram(-0.05);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().comments()).hasSize(1);
        assertThat(loaded.get().comments().get(0).from().name()).isEqualTo("ig_sam");
        assertThat(loaded.get().media()).hasSize(1);
        assertThat(loaded.get().totalLikes()).isEqualTo(10);
    }
}
