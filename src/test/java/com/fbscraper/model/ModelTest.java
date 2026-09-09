package com.fbscraper.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void shouldCreateAndVerifySentimentScore() {
        SentimentScore score = new SentimentScore(-0.65, 0.05, 0.20, 0.75, SentimentLevel.CRITICAL_NEGATIVE);

        assertThat(score.compound()).isEqualTo(-0.65);
        assertThat(score.isNegative()).isTrue();
        assertThat(score.level()).isEqualTo(SentimentLevel.CRITICAL_NEGATIVE);
    }

    @Test
    void shouldCreateFacebookPostWithComments() {
        Instant now = Instant.now();
        FacebookComment comment = new FacebookComment("c1", "Terrible service!", now);
        ReactionSummary reactions = new ReactionSummary(12, 6, 2, 1, 1, 0, 1, 1);
        FacebookPost post = new FacebookPost("p1", "Check our new product", now, List.of(comment), reactions);

        assertThat(post.id()).isEqualTo("p1");
        assertThat(post.comments()).hasSize(1);
        assertThat(post.comments().get(0).message()).isEqualTo("Terrible service!");
        assertThat(post.comments().get(0).reactions()).isEqualTo(ReactionSummary.empty());
        assertThat(post.reactions().total()).isEqualTo(12);
        assertThat(post.reactions().angry()).isEqualTo(1);
    }

    @Test
    void shouldStoreCommentReactions() {
        ReactionSummary reactions = new ReactionSummary(3, 1, 0, 2, 0, 0, 0, 0);
        FacebookComment comment = new FacebookComment("c2", "Thanks", Instant.now(), reactions);

        assertThat(comment.reactions().total()).isEqualTo(3);
        assertThat(comment.reactions().care()).isEqualTo(2);
    }

    @Test
    void shouldHandleNullCommentsGracefullyInPost() {
        FacebookPost post = new FacebookPost("p2", "Post without comments", Instant.now(), null, null);
        assertThat(post.comments()).isNotNull().isEmpty();
        assertThat(post.reactions()).isEqualTo(ReactionSummary.empty());
    }

    @Test
    void shouldCreateAndVerifyPageRatingSummary() {
        PageRatingSummary summary = new PageRatingSummary(4.6, 120);
        assertThat(summary.overallStarRating()).isEqualTo(4.6);
        assertThat(summary.ratingCount()).isEqualTo(120);
        assertThat(summary.hasRatings()).isTrue();

        assertThat(PageRatingSummary.EMPTY.hasRatings()).isFalse();
    }

    @Test
    void shouldCreateAndVerifyFacebookReviewAndAnalyzedReview() {
        Instant now = Instant.now();
        FacebookReview review = new FacebookReview(now, "positive", "Great staff!", 5, true);
        assertThat(review.isPositiveRecommendation()).isTrue();
        assertThat(review.isNegativeRecommendation()).isFalse();
        assertThat(review.reviewText()).isEqualTo("Great staff!");
        assertThat(review.rating()).isEqualTo(5);

        SentimentScore score = new SentimentScore(0.62, 0.4, 0.6, 0.0, SentimentLevel.POSITIVE);
        AnalyzedReview analyzedReview = new AnalyzedReview(review, score);
        assertThat(analyzedReview.score().compound()).isEqualTo(0.62);
        assertThat(analyzedReview.review().reviewText()).isEqualTo("Great staff!");
    }

    @Test
    void shouldCreateAndVerifyMessengerModels() {
        Instant now = Instant.now();
        FacebookParticipant user = new FacebookParticipant("u1", "Jane Doe", "jane@example.com");
        FacebookParticipant page = new FacebookParticipant("p1", "My Page", null);

        FacebookMessage msg = new FacebookMessage("m1", "Need help with order", now, user, List.of(page));
        FacebookConversation conv = new FacebookConversation("t1", now, List.of(user, page), List.of(msg));

        assertThat(conv.id()).isEqualTo("t1");
        assertThat(conv.messages()).hasSize(1);
        assertThat(conv.messages().get(0).from().name()).isEqualTo("Jane Doe");

        SentimentScore score = new SentimentScore(-0.6, 0.1, 0.3, 0.6, SentimentLevel.CRITICAL_NEGATIVE);
        AnalyzedMessage analyzedMsg = new AnalyzedMessage(msg, false, score, true);
        assertThat(analyzedMsg.isFromPage()).isFalse();
        assertThat(analyzedMsg.flagged()).isTrue();

        AnalyzedConversation analyzedConv = new AnalyzedConversation(
                "t1", now, List.of(user, page), List.of(analyzedMsg), SentimentLevel.CRITICAL_NEGATIVE, 1, 0
        );
        assertThat(analyzedConv.overallSentiment()).isEqualTo(SentimentLevel.CRITICAL_NEGATIVE);
        assertThat(analyzedConv.customerMessageCount()).isEqualTo(1);
        assertThat(analyzedConv.pageMessageCount()).isEqualTo(0);

        MessageSentimentSummary summary = new MessageSentimentSummary(1, 1, 1, 0, 0, 0, 0, 1, 100.0);
        assertThat(summary.criticalMessages()).isEqualTo(1);
        assertThat(summary.negativeRate()).isEqualTo(100.0);
    }

    @Test
    void shouldCreateAndVerifyFacebookUserAndReaction() {
        FacebookUser user = new FacebookUser("u100", "Alice Wonderland");
        assertThat(user.id()).isEqualTo("u100");
        assertThat(user.name()).isEqualTo("Alice Wonderland");
        assertThat(user.displayName()).isEqualTo("Alice Wonderland");

        FacebookUser anon = new FacebookUser("", "");
        assertThat(anon.displayName()).isEqualTo("Anonymous");

        FacebookReaction reaction = new FacebookReaction("u100", "Alice Wonderland", "LOVE");
        assertThat(reaction.type()).isEqualTo("LOVE");
        assertThat(reaction.user().id()).isEqualTo("u100");
    }

    @Test
    void shouldCreateCommentWithAuthorNestedRepliesAndReactors() {
        Instant now = Instant.now();
        FacebookUser author = new FacebookUser("u1", "John Doe");
        FacebookUser replier = new FacebookUser("u2", "Jane Smith");
        FacebookReaction commentReactor = new FacebookReaction("u3", "Bob", "LIKE");

        FacebookComment reply = new FacebookComment("r1", "I agree!", now, ReactionSummary.empty(), replier, List.of(), List.of());
        FacebookComment comment = new FacebookComment(
                "c1", "Great post!", now, new ReactionSummary(1, 1, 0, 0, 0, 0, 0, 0),
                author, List.of(reply), List.of(commentReactor)
        );

        assertThat(comment.from()).isEqualTo(author);
        assertThat(comment.replies()).hasSize(1);
        assertThat(comment.replies().get(0).from()).isEqualTo(replier);
        assertThat(comment.userReactions()).hasSize(1);
        assertThat(comment.userReactions().get(0).type()).isEqualTo("LIKE");
    }

    @Test
    void shouldCreatePostAndReviewWithUserDetails() {
        Instant now = Instant.now();
        FacebookReaction postReactor = new FacebookReaction("u4", "Charlie", "CARE");
        FacebookPost post = new FacebookPost("p1", "Hello", now, List.of(), ReactionSummary.empty(), List.of(postReactor));
        assertThat(post.userReactions()).containsExactly(postReactor);

        FacebookUser reviewer = new FacebookUser("u5", "David");
        FacebookReview review = new FacebookReview(now, "positive", "Super friendly", 5, true, reviewer);
        assertThat(review.reviewer()).isEqualTo(reviewer);
    }
}
