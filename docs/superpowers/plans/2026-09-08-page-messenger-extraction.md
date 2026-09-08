# Facebook Page Messenger Inbox Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract private conversations and messages from a Facebook Page's Messenger inbox, score customer message sentiment with VADER, render an interactive master-detail Messages tab in the web dashboard, and export both unified sync JSON and a dedicated `output/messages.json`.

**Architecture:** Extend `FacebookClient` to query `/{page-id}/conversations` with nested `messages` and cursor pagination. `SentimentSyncService` differentiates Page replies from customer messages, runs VADER sentiment analysis on customer messages, and aggregates thread-level and global statistics. `DataExportService` persists `output/messages.json` and updates `output/latest-sentiment.json`. The web dashboard adds a responsive Messages tab with search, sentiment filtering, and master-detail conversation bubbles.

**Tech Stack:** Java 21, Meta Graph API v26.0, Jackson, VADER Sentiment Analysis, JUnit 5, AssertJ, HTML5/CSS3/Vanilla JS.

## Global Constraints
- Maximum Meta limits by default: `fb.conversation.limit=100`, `fb.message.limit=100`.
- Page replies (`from.id == config.pageId()`) must not be evaluated as customer sentiment.
- Missing `pages_messaging` permission must fail gracefully with a descriptive warning instead of crashing the entire sync pipeline.

---

### Task 1: Configuration Support for Messenger Extraction

**Files:**
- Modify: `src/main/java/com/fbscraper/config/AppConfig.java`
- Modify: `src/main/resources/config.properties.template`
- Modify: `src/main/resources/config.properties`
- Test: `src/test/java/com/fbscraper/config/AppConfigTest.java`

**Interfaces:**
- Consumes: `Properties`
- Produces: `AppConfig.conversationLimit()` (int), `AppConfig.messageLimit()` (int)

- [ ] **Step 1: Update `AppConfigTest` with assertions for conversation & message limits**

Add test cases in `src/test/java/com/fbscraper/config/AppConfigTest.java`:
```java
@Test
void shouldDefaultConversationAndMessageLimitsTo100() {
    Properties props = new Properties();
    AppConfig config = AppConfig.fromProperties(props);

    assertThat(config.conversationLimit()).isEqualTo(100);
    assertThat(config.messageLimit()).isEqualTo(100);
}

@Test
void shouldParseExplicitConversationAndMessageLimits() {
    Properties props = new Properties();
    props.setProperty("fb.conversation.limit", "50");
    props.setProperty("fb.message.limit", "25");

    AppConfig config = AppConfig.fromProperties(props);

    assertThat(config.conversationLimit()).isEqualTo(50);
    assertThat(config.messageLimit()).isEqualTo(25);
}
```

- [ ] **Step 2: Run `AppConfigTest` to verify it fails**

Run: `./mvnw test -Dtest=AppConfigTest`
Expected: Compilation failure (method `conversationLimit()` not found).

- [ ] **Step 3: Update `AppConfig.java`, template, and active properties**

In `src/main/java/com/fbscraper/config/AppConfig.java`:
Add `int conversationLimit` and `int messageLimit` to the record components, define `DEFAULT_CONVERSATION_LIMIT = 100` and `DEFAULT_MESSAGE_LIMIT = 100`, and parse them in `fromProperties()`.

Update `src/main/resources/config.properties.template` and `src/main/resources/config.properties`:
```properties
# Maximum conversation threads to fetch (max allowed by Meta is 100)
fb.conversation.limit=100

# Maximum nested messages to fetch per conversation thread (max allowed by Meta is 100)
fb.message.limit=100
```

- [ ] **Step 4: Run `AppConfigTest` to verify it passes**

Run: `./mvnw test -Dtest=AppConfigTest`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add src/main/java/com/fbscraper/config/AppConfig.java src/main/resources/config.properties* src/test/java/com/fbscraper/config/AppConfigTest.java
git commit -m "feat(config): add fb.conversation.limit and fb.message.limit with default 100"
```

---

### Task 2: Data Models for Messenger Conversations and Sentiment

**Files:**
- Create: `src/main/java/com/fbscraper/model/FacebookParticipant.java`
- Create: `src/main/java/com/fbscraper/model/FacebookMessage.java`
- Create: `src/main/java/com/fbscraper/model/FacebookConversation.java`
- Create: `src/main/java/com/fbscraper/model/AnalyzedMessage.java`
- Create: `src/main/java/com/fbscraper/model/AnalyzedConversation.java`
- Create: `src/main/java/com/fbscraper/model/MessageSentimentSummary.java`
- Modify: `src/main/java/com/fbscraper/model/SyncResult.java`
- Test: `src/test/java/com/fbscraper/model/ModelTest.java`

**Interfaces:**
- Consumes: `SentimentScore`, `SentimentLevel`
- Produces: `FacebookConversation`, `AnalyzedConversation`, `MessageSentimentSummary`, updated `SyncResult`

- [ ] **Step 1: Write test cases in `ModelTest.java`**

Add tests for new models in `src/test/java/com/fbscraper/model/ModelTest.java`:
```java
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
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=ModelTest`
Expected: Compilation failure (unresolved symbols).

- [ ] **Step 3: Implement the model records**

Create `FacebookParticipant.java`:
```java
package com.fbscraper.model;

public record FacebookParticipant(String id, String name, String email) {}
```

Create `FacebookMessage.java`:
```java
package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record FacebookMessage(
        String id,
        String message,
        Instant createdTime,
        FacebookParticipant from,
        List<FacebookParticipant> to
) {
    public FacebookMessage {
        to = to == null ? List.of() : List.copyOf(to);
    }
}
```

Create `FacebookConversation.java`:
```java
package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record FacebookConversation(
        String id,
        Instant updatedTime,
        List<FacebookParticipant> participants,
        List<FacebookMessage> messages
) {
    public FacebookConversation {
        participants = participants == null ? List.of() : List.copyOf(participants);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
```

Create `AnalyzedMessage.java`:
```java
package com.fbscraper.model;

public record AnalyzedMessage(
        FacebookMessage rawMessage,
        boolean isFromPage,
        SentimentScore score,
        boolean flagged
) {}
```

Create `AnalyzedConversation.java`:
```java
package com.fbscraper.model;

import java.time.Instant;
import java.util.List;

public record AnalyzedConversation(
        String id,
        Instant updatedTime,
        List<FacebookParticipant> participants,
        List<AnalyzedMessage> messages,
        SentimentLevel overallSentiment,
        int customerMessageCount,
        int pageMessageCount
) {
    public AnalyzedConversation {
        participants = participants == null ? List.of() : List.copyOf(participants);
        messages = messages == null ? List.of() : List.copyOf(messages);
        overallSentiment = overallSentiment == null ? SentimentLevel.NEUTRAL : overallSentiment;
    }
}
```

Create `MessageSentimentSummary.java`:
```java
package com.fbscraper.model;

public record MessageSentimentSummary(
        int totalConversations,
        int totalMessages,
        int customerMessages,
        int pageReplies,
        int positiveMessages,
        int neutralMessages,
        int warningMessages,
        int criticalMessages,
        double negativeRate
) {
    public static final MessageSentimentSummary EMPTY = new MessageSentimentSummary(0, 0, 0, 0, 0, 0, 0, 0, 0.0);
}
```

Update `SyncResult.java` to append `MessageSentimentSummary messageSummary` and `List<AnalyzedConversation> conversations` (with safe backward-compatible defaults in constructors if needed).

- [ ] **Step 4: Run `ModelTest` to verify it passes**

Run: `./mvnw test -Dtest=ModelTest`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add src/main/java/com/fbscraper/model/ src/test/java/com/fbscraper/model/ModelTest.java
git commit -m "feat(model): add conversation, message, and sentiment models"
```

---

### Task 3: Graph API Client Integration for Conversations

**Files:**
- Modify: `src/main/java/com/fbscraper/client/FacebookClient.java`
- Modify: `src/test/java/com/fbscraper/client/FacebookClientTest.java`

**Interfaces:**
- Consumes: `AppConfig`
- Produces: `FacebookClient.ConversationPage`, `FacebookClient.fetchPageConversations()`, `FacebookClient.parseConversationsPage(String json)`

- [ ] **Step 1: Write test in `FacebookClientTest` for parsing conversations and nested messages**

In `src/test/java/com/fbscraper/client/FacebookClientTest.java`:
```java
@Test
void shouldParseConversationsWithParticipantsAndMessages() {
    String json = """
            {
              "data": [{
                "id": "t_100",
                "updated_time": "2026-07-02T15:00:00+0000",
                "participants": {
                  "data": [
                    {"id": "u_99", "name": "Alice User"},
                    {"id": "123", "name": "My Page"}
                  ]
                },
                "messages": {
                  "data": [{
                    "id": "m_1",
                    "message": "Where is my delivery?",
                    "created_time": "2026-07-02T14:55:00+0000",
                    "from": {"id": "u_99", "name": "Alice User"},
                    "to": {"data": [{"id": "123", "name": "My Page"}]}
                  }]
                }
              }],
              "paging": {"next": "https://graph.facebook.com/v26.0/123/conversations?after=cursor"}
            }
            """;

    FacebookClient.ConversationPage page = new FacebookClient(config()).parseConversationsPage(json);
    assertThat(page.conversations()).hasSize(1);
    FacebookConversation conv = page.conversations().get(0);
    assertThat(conv.id()).isEqualTo("t_100");
    assertThat(conv.participants()).hasSize(2);
    assertThat(conv.messages()).hasSize(1);
    assertThat(conv.messages().get(0).message()).isEqualTo("Where is my delivery?");
    assertThat(conv.messages().get(0).from().id()).isEqualTo("u_99");
    assertThat(page.nextUrl()).contains("after=cursor");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=FacebookClientTest#shouldParseConversationsWithParticipantsAndMessages`
Expected: Compilation failure.

- [ ] **Step 3: Implement `parseConversationsPage` and `fetchPageConversations` in `FacebookClient`**

Add `ConversationPage(List<FacebookConversation> conversations, String nextUrl)`.
Implement:
- `buildConversationsUrl()`
- `parseConversationsPage(String json)`
- `fetchPageConversations()`:
  - Graceful handling: If response status code is 400/403 with error mentioning permission/pages_messaging, log: `[FacebookClient] Page conversations inaccessible (missing pages_messaging permission?). Skipping inbox extraction.` and return `List.of()`.
  - Pagination across pages following `paging.next` up to `config.maxPages()`.

- [ ] **Step 4: Run `FacebookClientTest` to verify it passes**

Run: `./mvnw test -Dtest=FacebookClientTest`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add src/main/java/com/fbscraper/client/FacebookClient.java src/test/java/com/fbscraper/client/FacebookClientTest.java
git commit -m "feat(client): add conversation fetching, parsing, and error handling"
```

---

### Task 4: Sentiment Analysis & Thread Aggregation Service

**Files:**
- Modify: `src/main/java/com/fbscraper/service/SentimentSyncService.java`
- Modify: `src/test/java/com/fbscraper/AppE2ETest.java`

**Interfaces:**
- Consumes: `FacebookClient`, `VaderAnalyzer`, `AppConfig`
- Produces: `List<AnalyzedConversation>`, `MessageSentimentSummary`, populates `SyncResult`

- [ ] **Step 1: Write integration test verifying conversation analysis in `AppE2ETest`**

Extend `AppE2ETest` to mock `fetchPageConversations()`:
- 1 customer message with negative text ("Where is my shipment? This is unacceptable!").
- 1 page reply ("Sorry for the delay").
- Assert `result.messageSummary().customerMessages() == 1`, `result.messageSummary().pageReplies() == 1`, `result.messageSummary().criticalMessages() == 1` or `warningMessages() == 1`, and `result.conversations().get(0).messages().get(1).isFromPage() == true`.

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=AppE2ETest`
Expected: Failure / missing fields.

- [ ] **Step 3: Implement conversation sentiment analysis in `SentimentSyncService`**

In `SentimentSyncService.sync()`:
1. Fetch conversations from `facebookClient.fetchPageConversations()`.
2. For each conversation:
   - For each message:
     - Check if `from != null && from.id().equals(config.pageId())`.
     - If true: `isFromPage = true`, `score = null`, `flagged = false`.
     - If false: `isFromPage = false`, `score = analyzer.analyze(message.message())`, `flagged = score.compound() <= config.negativeThreshold()`.
   - Determine `overallSentiment` for the conversation (worst customer message level, or NEUTRAL).
   - Count customer messages and page replies.
3. Compute `MessageSentimentSummary` across all analyzed conversations.
4. Pass into `SyncResult`.

- [ ] **Step 4: Run `AppE2ETest` to verify it passes**

Run: `./mvnw test -Dtest=AppE2ETest`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add src/main/java/com/fbscraper/service/SentimentSyncService.java src/test/java/com/fbscraper/AppE2ETest.java
git commit -m "feat(service): analyze customer message sentiment and aggregate inbox threads"
```

---

### Task 5: Dedicated & Unified JSON Export

**Files:**
- Modify: `src/main/java/com/fbscraper/service/DataExportService.java`
- Test: `src/test/java/com/fbscraper/service/DataExportServiceTest.java` (create new or add to existing test)

**Interfaces:**
- Consumes: `SyncResult`
- Produces: `output/messages.json`, `output/messages-<timestamp>.json`, updated `latest-sentiment.json`

- [ ] **Step 1: Write test for `DataExportService` message export**

Verify that calling `export(result)` creates `output/messages.json` with `pageId`, `extractedAt`, `summary`, and `conversations`, and verify `loadLatest()` loads conversations back into `SyncResult`.

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=DataExportServiceTest`
Expected: Failure.

- [ ] **Step 3: Implement dedicated message export and loader in `DataExportService`**

In `DataExportService`:
- Create record `MessagesExport(String pageId, Instant extractedAt, MessageSentimentSummary summary, List<AnalyzedConversation> conversations)`.
- In `export(SyncResult result)`: Write `outputDir.resolve("messages.json")` and timestamped `outputDir.resolve("messages-" + timestamp + ".json")`.
- In `loadLatest()`: If `messages.json` exists, load conversations and reconstruct `MessageSentimentSummary`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=DataExportServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit changes**

```bash
git add src/main/java/com/fbscraper/service/DataExportService.java src/test/java/com/fbscraper/
git commit -m "feat(export): export dedicated output/messages.json and load previous inbox state"
```

---

### Task 6: Web Dashboard Integration ("Messages" Tab)

**Files:**
- Modify: `src/main/resources/web/index.html`
- Modify: `src/test/java/com/fbscraper/report/HtmlDashboardGeneratorTest.java` (or WebServer test)

**Interfaces:**
- Consumes: JSON from `/api/status` & `/api/sync` containing `messageSummary` and `conversations`
- Produces: Interactive 4th tab with search, sentiment filter chips, thread list, and chat bubble viewer

- [ ] **Step 1: Update `index.html` structure and styling**

1. Add `<button class="tab-button" data-tab="messages" type="button">Messages</button>` in `<nav class="tabs">`.
2. Add `<section class="tab-panel" data-panel="messages" hidden>`:
   - Summary statistics banner (Total Threads, Messages, Customer Inquiries, Page Replies, Negative Rate).
   - Filter bar: search input + sentiment filter buttons (`All`, `Flagged`, `Critical`, `Positive`).
   - Two-column split layout:
     - Left pane (`#conversationList`): List of threads.
     - Right pane (`#activeConversation`): Active thread conversation history with message bubbles and sentiment indicators.
     - Empty/fallback state if no conversations are loaded or if `pages_messaging` permission is required.

- [ ] **Step 2: Add JavaScript handler in `index.html` for rendering messages**

1. In `renderDashboard(data)`: update messages metrics, store conversations array, render thread list.
2. Add selection event: clicking a thread renders the chat bubbles in the right pane.
3. Add search & filter event listeners.

- [ ] **Step 3: Verify with `LocalWebServerTest` and browser checks**

Run: `./mvnw test`
Verify that existing tests pass, and manually test that the dashboard loads the Messages tab and renders mock/synced data cleanly.

- [ ] **Step 4: Commit changes**

```bash
git add src/main/resources/web/index.html src/test/java/com/fbscraper/
git commit -m "feat(web): add interactive Messages tab to dashboard"
```

---

### Task 7: Documentation & Verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update `README.md` with Messenger feature documentation**

Document:
- `pages_messaging` permission requirement in Meta Graph API Explorer.
- `fb.conversation.limit` and `fb.message.limit` settings in `config.properties`.
- Dedicated `output/messages.json` output description.
- New **Messages** dashboard tab guide.

- [ ] **Step 2: Run all tests**

Run: `./mvnw clean test`
Expected: All tests pass with 0 failures, 0 errors.

- [ ] **Step 3: Commit changes**

```bash
git add README.md
git commit -m "docs: document page messenger inbox extraction and configuration"
```
