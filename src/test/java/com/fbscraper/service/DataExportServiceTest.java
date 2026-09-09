package com.fbscraper.service;

import com.fbscraper.model.AnalyzedConversation;
import com.fbscraper.model.AnalyzedMessage;
import com.fbscraper.model.FacebookMessage;
import com.fbscraper.model.FacebookParticipant;
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

        FacebookParticipant customer = new FacebookParticipant("u1", "Alice");
        FacebookParticipant page = new FacebookParticipant("123", "Page");

        FacebookMessage msg1 = new FacebookMessage("m1", "I love this service!", now, customer, List.of(page));
        SentimentScore score1 = new SentimentScore(0.7, 0.4, 0.6, 0.0, SentimentLevel.POSITIVE);
        AnalyzedMessage analyzedMsg1 = new AnalyzedMessage(msg1, false, score1, false);

        FacebookMessage msg2 = new FacebookMessage("m2", "Thank you Alice!", now.plusSeconds(30), page, List.of(customer));
        AnalyzedMessage analyzedMsg2 = new AnalyzedMessage(msg2, true, null, false);

        AnalyzedConversation conv = new AnalyzedConversation(
                "t1",
                now.plusSeconds(30),
                List.of(customer, page),
                List.of(analyzedMsg1, analyzedMsg2),
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

        com.fbscraper.model.FacebookUser commenter = new com.fbscraper.model.FacebookUser("u10", "Commenter Sam");
        com.fbscraper.model.FacebookUser replier = new com.fbscraper.model.FacebookUser("u11", "Replier Sue");
        com.fbscraper.model.FacebookReaction commentReactor = new com.fbscraper.model.FacebookReaction("u12", "Reactor Ron", "HAHA");
        com.fbscraper.model.FacebookReaction postReactor = new com.fbscraper.model.FacebookReaction("u13", "Reactor Ray", "WOW");
        com.fbscraper.model.FacebookUser reviewer = new com.fbscraper.model.FacebookUser("u14", "Reviewer Rita");

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

        com.fbscraper.model.FacebookReview fbReview = new com.fbscraper.model.FacebookReview(
                now, "positive", "Loved it!", 5, true, reviewer
        );
        com.fbscraper.model.AnalyzedReview review = new com.fbscraper.model.AnalyzedReview(
                fbReview, new SentimentScore(0.8, 0.6, 0.4, 0.0, SentimentLevel.POSITIVE)
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
        assertThat(loaded.get().reviews().get(0).review().reviewer().name()).isEqualTo("Reviewer Rita");
    }
}
