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
}
