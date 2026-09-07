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
        assertThat(result.comments()).anySatisfy(comment -> {
            assertThat(comment.message()).contains("HORRIBLE");
            assertThat(comment.flagged()).isTrue();
        });
        assertThat(result.positiveComments() + result.neutralComments()
                + result.warningComments() + result.criticalComments())
                .isEqualTo(result.totalComments());
    }
}
