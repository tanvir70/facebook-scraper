# Facebook Scraper

A Java 21 Facebook Page monitoring application. It fetches posts, comments, reactions, ratings, and reviews through the Meta Graph API, analyzes comment and review sentiment with VADER, and presents the results in a local browser dashboard.

## Features

- Cursor pagination for Page posts and reviews.
- Configurable posts per page, comments per post, nested comments limit, reaction limit, and maximum pages.
- Detailed commenter user details (`id`, `name`) and full nested comment reply threads with recursive sentiment analysis.
- Post and comment reactor user details (`id`, `name`, and reaction `type`) alongside reaction aggregates: `LIKE`, `LOVE`, `CARE`, `HAHA`, `WOW`, `SAD`, and `ANGRY`.
- Page recommendations and customer reviews with reviewer details (`id`, `name`) and ratings.
- Page Messenger inbox conversation and direct message extraction with media/file attachments.
- Local VADER sentiment analysis for comments, nested replies, reviews, and customer messages.
- A **Sync now** browser UI at `http://localhost:8080`.
- Separate dashboard tabs for comments (with nested reply threads and author chips), post reactions (with expandable reactor lists), reviews (with reviewer badges), and messages.
- Comment reactions and reactor details displayed on their individual comment rows.
- Dedicated JSON data exports (`output/messages.json`, `output/comments.json`, `output/reviews.json`, `output/posts.json`, `output/sync-result.json`).
- Helpful expired-token errors without storing a Facebook password.

## Meta permissions

Generate a Page access token with:

- `pages_show_list`
- `pages_read_engagement`
- `pages_read_user_content`
- `pages_messaging` (required to read Page inbox conversations, messages, and attachments)

Open the [Meta Graph API Explorer](https://developers.facebook.com/tools/explorer/), generate a User token with those permissions, then call:

```text
/me/accounts?fields=id,name,access_token,tasks
```

Copy the Page ID and Page access token returned for the Page. Meta documents `CARE` as a supported reaction type, but some Like metrics may also include Care activity. The dashboard displays the separate CARE value returned by the reactions endpoint.

> **Privacy Note:** In accordance with Meta Graph API privacy guidelines (v3.0+), user identities in public interactions (reactions, comments, reviews) are represented by their public display `name` and Page-Scoped ID (`PSID` / `ASUID`). Meta does not expose email addresses or phone numbers via public Graph API interaction endpoints.

> **Note:** If `pages_messaging` is missing on your token, the application will log a clear warning and proceed with fetching posts, comments, reactions, and reviews without failing.

## Configuration

Copy the template:

```bash
cp src/main/resources/config.properties.template config.properties
```

Edit `config.properties`:

```properties
fb.page.id=YOUR_FACEBOOK_PAGE_ID
fb.access.token=YOUR_PAGE_ACCESS_TOKEN
fb.api.version=v26.0

# Posts returned by each feed request (max 100)
fb.feed.limit=100

# Top-level comments returned for each post (max 100)
fb.comment.limit=100

# Nested comment replies returned for each comment (max 100)
fb.nested_comment.limit=100

# Interacting users returned for reactions on posts and comments (max 100)
fb.reaction.limit=100

# Conversations per page (max 100)
fb.conversation.limit=100

# Messages per conversation thread (max 100)
fb.message.limit=100

# Feed/review/conversation pages to follow; use 0 for unlimited
fb.max.pages=5

app.negative.threshold=-0.05
```

The application reads `config.properties` from the working directory, falling back to `src/main/resources/config.properties`.

## Run

```bash
./mvnw clean package
java -jar target/facebook-scraper-1.0.0-SNAPSHOT.jar
```

Open:

```text
http://localhost:8080
```

Use a different port when needed:

```bash
java -jar target/facebook-scraper-1.0.0-SNAPSHOT.jar --port=9090
```

Click **Sync now** to run the complete collection, sentiment analysis, and JSON export pipeline.

## Dashboard organization

- **Comments:** Default view containing comment text, commenter name/ID, parent-post context, sentiment, score, timestamp, comment reaction count/reactors, and nested reply threads with author badges.
- **Post reactions:** Post-level reaction totals, per-type breakdown, and expandable reactor list chips showing who reacted and their reaction emoji/type.
- **Reviews:** Page recommendations/reviews with reviewer name/ID badge, ratings, and sentiment.
- **Messages:** Messenger inbox conversations and customer chat threads:
  - Left panel: Searchable thread list showing customer name, message count, latest message timestamp, and customer sentiment badge.
  - Right panel: Full chat transcript with distinct customer (left) and Page (right) bubbles, VADER sentiment scores, and attachment previews (images rendered directly, downloadable files linked).

## JSON Data Exports

Every sync writes structured JSON files into the `output/` directory (created automatically):

- `output/messages.json`: All analyzed Messenger conversations, thread sentiment summaries, customer messages, Page replies, and attachments.
- `output/comments.json`: Analyzed post comments with author details (`from{id, name}`), nested reply threads (`replies[]`), reaction summaries, and individual reactor details (`userReactions[]`).
- `output/reviews.json`: Page reviews and ratings with reviewer details (`reviewer{id, name}`) and sentiment labels.
- `output/posts.json`: Page posts with reaction breakdowns and interacting reactors (`userReactions[]`).
- `output/sync-result.json`: Full aggregate sync payload.

## Pagination

The first request uses `fb.feed.limit`, `fb.comment.limit`, and `fb.conversation.limit`. When Meta returns `paging.next`, the collector follows it until there is no next cursor or `fb.max.pages` is reached.

## Tests

```bash
./mvnw test
```

## Main components

```text
src/main/java/com/fbscraper/
├── App.java
├── client/FacebookClient.java
├── config/AppConfig.java
├── model/
│   ├── FacebookAttachment.java
│   ├── FacebookConversation.java
│   ├── FacebookMessage.java
│   ├── FacebookParticipant.java
│   ├── FacebookUser.java
│   ├── FacebookReaction.java
│   ├── FacebookComment.java
│   ├── FacebookPost.java
│   ├── FacebookReview.java
│   ├── AnalyzedConversation.java
│   ├── AnalyzedMessage.java
│   ├── CommentAnalysis.java
│   ├── PostReactionAnalysis.java
│   └── MessageSentimentSummary.java
├── sentiment/VaderAnalyzer.java
├── service/
│   ├── DataExportService.java
│   └── SentimentSyncService.java
├── report/HtmlDashboardGenerator.java
└── web/LocalWebServer.java

src/main/resources/
├── config.properties.template
├── vader_lexicon.txt
└── web/index.html
```
