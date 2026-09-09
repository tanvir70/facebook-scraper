package com.fbscraper;

import com.fbscraper.config.AppConfig;
import com.fbscraper.service.DataExportService;
import com.fbscraper.service.SentimentSyncService;
import com.fbscraper.web.SyncApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "fb.page-id=test-page",
        "fb.access-token=test-token"
})
class FacebookScraperContextTest {

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private SyncApiController syncApiController;

    @Autowired
    private SentimentSyncService sentimentSyncService;

    @Autowired
    private DataExportService dataExportService;

    @Test
    void shouldLoadSpringApplicationContext() {
        assertThat(appConfig).isNotNull();
        assertThat(appConfig.pageId()).isEqualTo("test-page");
        assertThat(appConfig.accessToken()).isEqualTo("test-token");
        assertThat(syncApiController).isNotNull();
        assertThat(sentimentSyncService).isNotNull();
        assertThat(dataExportService).isNotNull();
    }
}
