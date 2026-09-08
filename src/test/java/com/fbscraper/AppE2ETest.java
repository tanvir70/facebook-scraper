package com.fbscraper;

import com.fbscraper.config.AppConfig;
import com.fbscraper.model.SyncResult;
import com.fbscraper.service.SentimentSyncService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppE2ETest {

    @Test
    void shouldFetchAndAnalyzeTheOfflineFeed() {
        AppConfig config = new AppConfig("", "", "v26.0", true, -0.05);

        SyncResult result = new SentimentSyncService(config).sync();

        assertThat(result.totalPosts()).isEqualTo(2);
        assertThat(result.totalComments()).isEqualTo(8);
        assertThat(result.totalReactions()).isEqualTo(23);
        assertThat(result.reactionTotals().like()).isEqualTo(11);
        assertThat(result.reactionTotals().angry()).isEqualTo(5);
        assertThat(result.postReactions()).hasSize(2);
        assertThat(result.totalCommentReactions()).isEqualTo(26);
        assertThat(result.commentReactionTotals().care()).isEqualTo(1);
        assertThat(result.commentReactionTotals().angry()).isEqualTo(9);
        assertThat(result.comments()).anySatisfy(comment -> {
            assertThat(comment.message()).contains("HORRIBLE");
            assertThat(comment.flagged()).isTrue();
        });
        assertThat(result.comments()).anySatisfy(comment -> {
            assertThat(comment.commentId()).isEqualTo("comm_5");
            assertThat(comment.reactions().care()).isEqualTo(1);
        });
        assertThat(result.positiveComments() + result.neutralComments()
                + result.warningComments() + result.criticalComments())
                .isEqualTo(result.totalComments());
    }
}
