# Facebook Page Messenger Inbox Extraction & Sentiment Design

## Overview
This feature extends the Facebook Page Scraper to extract private conversations and messages from a Facebook Page's Messenger inbox via the Meta Graph API. It classifies messages between customers and Page administrators, evaluates customer sentiment using VADER, presents the conversation threads in an interactive master-detail dashboard tab, and exports the data into both a unified sync JSON and a dedicated `output/messages.json` file.

---

## 1. Configuration

Add two new configuration parameters in `AppConfig` and `config.properties.template`:
* `fb.conversation.limit`: Maximum conversations to fetch per page request (default: `100`, which is the maximum supported by the Graph API).
* `fb.message.limit`: Maximum messages to fetch per conversation thread (default: `100`, the maximum supported by Graph API nested limits).

Existing parameters reused:
* `fb.page.id`: Used to identify the Page and distinguish Page replies from customer messages.
* `fb.access.token`: Must have `pages_messaging` permission.
* `fb.max.pages`: Follows `paging.next` cursors up to `maxPages` (0 for unlimited).
* `app.negative.threshold`: Threshold (default: `-0.05`) below which customer messages are flagged as negative.

---

## 2. Meta Graph API Integration

### Endpoint
```text
GET /{page-id}/conversations?fields=id,updated_time,participants,messages.limit({msg_limit}){id,message,created_time,from,to}&limit={conv_limit}
```

### Authentication & Permissions
* Page Access Token requires `pages_messaging` (and standard Page management permissions).
* If the token lacks `pages_messaging` or returns an authorization error for conversations, `FacebookClient` logs a clear warning explaining the missing permission and returns an empty list, allowing the rest of the sync (feed, comments, reviews) to proceed without crashing.

### Pagination
* Follows `root.path("paging").path("next")` up to `config.maxPages()` or until no next cursor is returned.

---

## 3. Data Models (`com.fbscraper.model`)

### Raw Graph API Models
1. **`FacebookParticipant`**:
   * `String id`
   * `String name`
   * `String email`
2. **`FacebookMessage`**:
   * `String id`
   * `String message`
   * `Instant createdTime`
   * `FacebookParticipant from`
   * `List<FacebookParticipant> to`
3. **`FacebookConversation`**:
   * `String id`
   * `Instant updatedTime`
   * `List<FacebookParticipant> participants`
   * `List<FacebookMessage> messages`

### Sentiment & Analysis Models
4. **`AnalyzedMessage`**:
   * `FacebookMessage rawMessage`
   * `boolean isFromPage`: `true` if `from.id == config.pageId()`, `false` if customer
   * `SentimentScore score`: VADER score for customer messages; `null` for Page responses
   * `boolean flagged`: `true` if customer message compound score <= `negativeThreshold`
5. **`AnalyzedConversation`**:
   * `String id`
   * `Instant updatedTime`
   * `List<FacebookParticipant> participants`
   * `List<AnalyzedMessage> messages`
   * `SentimentLevel overallSentiment`: determined by customer sentiment
   * `int customerMessageCount`
   * `int pageMessageCount`
6. **`MessageSentimentSummary`**:
   * `int totalConversations`
   * `int totalMessages`
   * `int customerMessages`
   * `int pageReplies`
   * `int positiveMessages`
   * `int neutralMessages`
   * `int warningMessages`
   * `int criticalMessages`
   * `double negativeRate`

---

## 4. Processing Pipeline (`SentimentSyncService`)

1. **Extraction**: Calls `facebookClient.fetchPageConversations()`.
2. **Analysis**:
   * For each message, check if sender ID matches `config.pageId()`.
   * If Page reply: mark `isFromPage = true`.
   * If customer message: run `analyzer.analyze(message.message())`, compute level and flagged status.
3. **Aggregation**:
   * Compute per-conversation metrics and overall conversation sentiment level.
   * Compute aggregate `MessageSentimentSummary`.
4. **Result Integration**:
   * `SyncResult` record is extended to include `MessageSentimentSummary messageSummary` and `List<AnalyzedConversation> conversations`.

---

## 5. Export Services (`DataExportService`)

1. **Unified Export**: Included in `output/latest-sentiment.json` and timestamped `output/sentiment-<timestamp>.json`.
2. **Dedicated Export**: Written to `output/messages.json` and timestamped `output/messages-<timestamp>.json` with the following structure:
   * `pageId`: string
   * `extractedAt`: ISO-8601 timestamp
   * `summary`: `MessageSentimentSummary`
   * `conversations`: list of `AnalyzedConversation`

---

## 6. Web Dashboard UI (`index.html`)

1. **Navigation**: Add a 4th tab button: `[Messages]` alongside `[Comments]`, `[Post reactions]`, and `[Reviews]`.
2. **Tab Banner**: Display counters for Total Conversations, Total Messages (Inquiries vs. Replies), and Negative Inquiry Rate.
3. **Split Master-Detail View**:
   * **Left Panel**: Filter chips (`All`, `Flagged`, `Critical`, `Positive`), text search, and list of conversation cards showing customer name, last message snippet, timestamp, and thread sentiment badge.
   * **Right Panel**: Chat history viewer showing selected thread with message bubbles:
     * Customer messages on the left with sentiment score and level tag.
     * Page replies on the right with Page branding.
4. **Empty State / Permission Notice**: Informative banner when no messages exist or when `pages_messaging` permission is required.

---

## 7. Verification & Testing

* **`AppConfigTest`**: Unit test confirming `fb.conversation.limit` (default: 100) and `fb.message.limit` (default: 100) are parsed correctly.
* **`FacebookClientTest`**: Unit test parsing mock conversation JSON with nested messages, pagination, and error handling for missing permissions.
* **`SentimentSyncServiceTest`**: Unit test verifying customer message scoring, page message differentiation, and summary calculations.
* **`DataExportServiceTest`**: Unit test validating `output/messages.json` file creation and JSON schema.
* **`AppE2ETest`**: End-to-end test confirming the web server serves the Messages tab and responds with the updated payload on `/api/status`.
