package com.fbscraper.web;

import com.fbscraper.client.FacebookClient;
import com.fbscraper.config.AppConfig;
import com.fbscraper.model.Comment;
import com.fbscraper.model.Conversation;
import com.fbscraper.model.PageRatingSummary;
import com.fbscraper.model.Post;
import com.fbscraper.model.Reaction;
import com.fbscraper.model.ReactionSummary;
import com.fbscraper.model.Review;
import com.fbscraper.model.User;
import com.fbscraper.sentiment.VaderAnalyzer;
import com.fbscraper.service.DataExportService;
import com.fbscraper.service.SentimentSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncApiControllerTest {

    private MockMvc mockMvc;
    private SentimentSyncService syncService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05);
        Instant now = Instant.parse("2026-09-08T08:00:00Z");
        User commenter = new User("u1", "Commenter Alice");
        User replier = new User("u2", "Replier Bob");
        Reaction commentReactor = new Reaction("u3", "Reactor Carl", "LIKE");
        Reaction postReactor = new Reaction("u4", "Reactor Dan", "LOVE");
        User reviewer = new User("u5", "Reviewer Eve");

        Comment reply = new Comment("r1", "Reply text", now, ReactionSummary.empty(), replier, List.of(), List.of());
        Comment comment = new Comment(
                "c1", "Awful support", now, new ReactionSummary(2, 1, 0, 0, 0, 0, 0, 1),
                commenter, List.of(reply), List.of(commentReactor)
        );
        Post post = new Post(
                "p1", "Support", now, List.of(comment), new ReactionSummary(5, 2, 0, 1, 0, 0, 0, 2),
                List.of(postReactor)
        );

        FacebookClient facebookClient = new FacebookClient(config, request -> {
            throw new AssertionError("Unexpected HTTP call");
        }) {
            @Override public List<Post> fetchPageFeed() { return List.of(post); }
            @Override public PageRatingSummary fetchPageRatingSummary() { return new PageRatingSummary(4.2, 10); }
            @Override public List<Review> fetchPageReviews() {
                return List.of(new Review(now, "positive", "Good", 5, true, reviewer));
            }
            @Override public List<Conversation> fetchPageConversations() {
                return List.of();
            }
        };

        syncService = new SentimentSyncService(
                config, facebookClient, VaderAnalyzer.createDefault(), new DataExportService(tempDir)
        );
        SyncApiController controller = new SyncApiController(syncService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnNotSyncedWhenNoSyncRan() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_SYNCED"))
                .andExpect(jsonPath("$.syncing").value(false));
    }

    @Test
    void shouldRunSyncAndReturnResult() throws Exception {
        mockMvc.perform(post("/api/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPosts").value(1))
                .andExpect(jsonPath("$.totalComments").value(1))
                .andExpect(jsonPath("$.totalReactions").value(5))
                .andExpect(jsonPath("$.totalCommentReactions").value(2))
                .andExpect(jsonPath("$.totalReviews").value(1))
                .andExpect(jsonPath("$.comments[0].from.name").value("Commenter Alice"))
                .andExpect(jsonPath("$.comments[0].replies[0].from.name").value("Replier Bob"))
                .andExpect(jsonPath("$.comments[0].userReactions[0].name").value("Reactor Carl"))
                .andExpect(jsonPath("$.postReactions[0].userReactions[0].name").value("Reactor Dan"))
                .andExpect(jsonPath("$.reviews[0].reviewer.name").value("Reviewer Eve"));

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPosts").value(1))
                .andExpect(jsonPath("$.totalComments").value(1));
    }

    @Test
    void shouldReturnBadGatewayOnError() throws Exception {
        AppConfig config = new AppConfig("123", "token", "v26.0", 100, 100, 5, -0.05);
        FacebookClient failingClient = new FacebookClient(config, request -> {
            throw new AssertionError("Unexpected call");
        }) {
            @Override public List<Post> fetchPageFeed() {
                throw new IllegalStateException("Facebook API timeout");
            }
        };
        SentimentSyncService failingService = new SentimentSyncService(
                config, failingClient, VaderAnalyzer.createDefault(), new DataExportService()
        );
        SyncApiController controller = new SyncApiController(failingService);
        MockMvc failMvc = MockMvcBuilders.standaloneSetup(controller).build();

        failMvc.perform(post("/api/sync"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Facebook API timeout"));
    }
}
