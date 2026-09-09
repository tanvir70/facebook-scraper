package com.fbscraper.service;

import com.fbscraper.model.Conversation;
import com.fbscraper.model.Message;
import com.fbscraper.model.Reaction;
import com.fbscraper.model.Review;
import com.fbscraper.model.User;
import com.fbscraper.model.MessageSentimentSummary;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.ReactionSummary;
import com.fbscraper.enums.SentimentLevel;
import com.fbscraper.model.SentimentScore;
import com.fbscraper.model.SyncResult;
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

        User customer = new User("u1", "Alice");
        User page = new User("123", "Page");

        SentimentScore score1 = new SentimentScore(0.7, 0.4, 0.6, 0.0, SentimentLevel.POSITIVE);
        Message msg1 = new Message("m1", "I love this service!", now, customer, List.of(page), List.of(), false, score1, false);

        Message msg2 = new Message("m2", "Thank you Alice!", now.plusSeconds(30), page, List.of(customer), List.of(), true, null, false);

        Conversation conv = new Conversation(
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

        SyncResult result = new SyncResult(
                now,
                -0.05,
                0,
                0,
                0,
                ReactionSummary.empty(),
                0,
                ReactionSummary.empty(),
                0,
                0,
                0,
                0,
                0,
                0.0,
                List.of(),
                List.of(),
                PageRatingSummary.EMPTY,
                0,
                0,
                List.of(),
                summary,
                List.of(conv)
        );

        exportService.export(result);

        Path messagesFile = tempDir.resolve("messages.json");
        assertThat(Files.exists(messagesFile)).isTrue();

        Optional<SyncResult> loaded = exportService.loadLatest(-0.05);
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

        User commenter = new User("u10", "Commenter Sam");
        User replier = new User("u11", "Replier Sue");
        Reaction commentReactor = new Reaction("u12", "Reactor Ron", "HAHA");
        Reaction postReactor = new Reaction("u13", "Reactor Ray", "WOW");
        User reviewer = new User("u14", "Reviewer Rita");

        com.fbscraper.model.CommentAnalysis reply = new com.fbscraper.model.CommentAnalysis(
                "r1", "p1", "Snippet", "Replying to Sam", now, 0.2, SentimentLevel.POSITIVE, false,
                ReactionSummary.empty(), replier, List.of(), List.of()
        );
        com.fbscraper.model.CommentAnalysis comment = new com.fbscraper.model.CommentAnalysis(
                "c1", "p1", "Snippet", "Great product", now, 0.6, SentimentLevel.POSITIVE, false,
                new ReactionSummary(1, 0, 0, 0, 1, 0, 0, 0), commenter, List.of(reply), List.of(commentReactor)
        );

        com.fbscraper.model.PostReactionAnalysis postReaction = new com.fbscraper.model.PostReactionAnalysis(
                "p1", "Snippet", now, new ReactionSummary(1, 0, 0, 0, 0, 1, 0, 0), List.of(postReactor)
        );

        Review review = new Review(
                now, "positive", "Loved it!", 5, true, reviewer,
                new SentimentScore(0.8, 0.6, 0.4, 0.0, SentimentLevel.POSITIVE)
        );

        SyncResult result = new SyncResult(
                now, -0.05, 1, 1, 1, new ReactionSummary(1, 0, 0, 0, 0, 1, 0, 0),
                1, new ReactionSummary(1, 0, 0, 0, 1, 0, 0, 0),
                1, 0, 0, 0, 0, 0.0,
                List.of(comment), List.of(postReaction), PageRatingSummary.EMPTY,
                1, 0, List.of(review), MessageSentimentSummary.EMPTY, List.of()
        );

        exportService.export(result);

        Path commentsFile = tempDir.resolve("comments.json");
        Path postReactionsFile = tempDir.resolve("post_reactions.json");
        Path reviewsFile = tempDir.resolve("reviews.json");

        assertThat(Files.exists(commentsFile)).isTrue();
        assertThat(Files.exists(postReactionsFile)).isTrue();
        assertThat(Files.exists(reviewsFile)).isTrue();

        Optional<SyncResult> loaded = exportService.loadLatest(-0.05);
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
}
