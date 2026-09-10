# Social Monitor (Facebook & Instagram)

A Java 21 social monitoring application for Facebook Pages and Instagram Professional accounts. It fetches posts, media, comments, nested replies, reactions, ratings, reviews, and direct messages through the Meta Graph API, analyzes comment and message sentiment with VADER, and presents the results in a local browser dashboard with a platform switcher.

## Features

- **Multi-Platform Support:** Instant switching between **Facebook** and **Instagram** on the web dashboard.
- **Instagram Media & Comments:** Auto-resolves linked Instagram Business accounts, fetches media items (photos, carousels, reels, videos), comments, and nested replies with username details.
- **Instagram Direct Messages:** Fetches customer direct message conversations, timestamps, sender badges, and attachments via the Instagram Messaging API.
- **Cursor pagination:** For Facebook Page posts/reviews and Instagram media/conversations.
- **Configurable limits:** Posts/media per page, comments per post, nested comments limit, reaction limit, and maximum pages.
- **Detailed interaction analysis:** Commenter identity (`id`, `name`, `username`) and full nested comment reply threads with recursive sentiment analysis.
- **Reactions & Likes:** Full 7-reaction breakdown on Facebook (`LIKE`, `LOVE`, `CARE`, `HAHA`, `WOW`, `SAD`, `ANGRY`) with reactor lists, and aggregate Like metrics on Instagram.
- **Ratings & Reviews:** Facebook Page recommendations and customer reviews with ratings and sentiment.
- **Local VADER sentiment analysis:** For comments, nested replies, reviews, and customer direct messages.
- **Sync now browser UI:** At `http://localhost:8080`.
- **Dedicated JSON data exports:**
  - Facebook: `output/messages.json`, `output/comments.json`, `output/reviews.json`, `output/post_reactions.json`, `output/sync-result.json`
  - Instagram: `output/instagram_messages.json`, `output/instagram_comments.json`, `output/instagram_post_reactions.json`, `output/instagram_sync-result.json`
- **Helpful expired-token errors:** Clear diagnostics when Meta tokens expire.

## Meta permissions

Generate a Page access token with:

- `pages_show_list`
- `pages_read_engagement`
- `pages_read_user_content`
- `pages_messaging` (required to read Facebook Page inbox conversations)
- `instagram_basic` (required for Instagram media and profile info)
- `instagram_manage_comments` (required for Instagram comments and replies)
- `instagram_manage_messages` (required for Instagram Direct Messages)

Open the [Meta Graph API Explorer](https://developers.facebook.com/tools/explorer/), generate a User token with those permissions, then call:

```text
/me/accounts?fields=id,name,access_token,tasks
```

Copy the Page ID and Page access token returned for the Page. Meta documents `CARE` as a supported reaction type, but some Like metrics may also include Care activity. The dashboard displays the separate CARE value returned by the reactions endpoint.

> **Privacy Note:** In accordance with Meta Graph API privacy guidelines (v3.0+), user identities in public interactions (reactions, comments, reviews) are represented by their public display `name` and Page-Scoped ID (`PSID` / `ASUID`). Meta does not expose email addresses or phone numbers via public Graph API interaction endpoints.

> **Note:** If `pages_messaging` is missing on your token, the application will log a clear warning and proceed with fetching posts, comments, reactions, and reviews without failing.

### Configuration

Copy the template:

```bash
cp src/main/resources/application.properties.template src/main/resources/application.properties
```

Edit `application.properties`:

```properties
server.port=8080

fb.page-id=YOUR_FACEBOOK_PAGE_ID
fb.access-token=YOUR_PAGE_ACCESS_TOKEN
fb.api-version=v26.0

# Posts returned by each feed request (max 100)
fb.feed-limit=100

# Top-level comments returned for each post (max 100)
fb.comment-limit=100

# Nested comment replies returned for each comment (max 100)
fb.nested-comment-limit=100

# Interacting users returned for reactions on posts and comments (max 100)
fb.reaction-limit=100

# Conversations per page (max 100)
fb.conversation-limit=100

# Messages per conversation thread (max 100)
fb.message-limit=100

# Feed/review/conversation pages to follow; use 0 for unlimited
fb.max-pages=5

# Sentiment threshold
fb.negative-threshold=-0.05
```

The application loads standard Spring Boot configuration properties and also supports environment variables or command-line overrides.

## Run

Run directly from Gradle:

```bash
./gradlew bootRun
```

Or build the executable JAR and run:

```bash
./gradlew bootJar
java -jar build/libs/facebook-scraper-1.0.0-SNAPSHOT.jar
```

Open:

```text
http://localhost:8080
```

Use a different port when needed:

```bash
./gradlew bootRun --args='--server.port=9090'
# or with JAR:
java -jar build/libs/facebook-scraper-1.0.0-SNAPSHOT.jar --server.port=9090
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

The first request uses `fb.feed-limit`, `fb.comment-limit`, and `fb.conversation-limit`. When Meta returns `paging.next`, the collector follows it until there is no next cursor or `fb.max-pages` is reached.

## Tests

```bash
./gradlew test
```

## Main components

```text
src/main/java/com/fbscraper/
├── App.java
├── client/
│   ├── FacebookClient.java
│   ├── GraphResponseParser.java
│   ├── GraphUrlBuilder.java
│   ├── InstagramClient.java
│   ├── InstagramResponseParser.java
│   └── InstagramUrlBuilder.java
├── config/AppConfig.java
├── enums/SentimentLevel.java
├── model/
│   ├── MessageSentimentSummary.java
│   ├── SentimentScore.java
│   ├── facebook/
│   │   ├── FacebookAttachment.java
│   │   ├── FacebookComment.java
│   │   ├── FacebookCommentAnalysis.java
│   │   ├── FacebookConversation.java
│   │   ├── FacebookMessage.java
│   │   ├── FacebookPageRatingSummary.java
│   │   ├── FacebookPost.java
│   │   ├── FacebookPostReactionAnalysis.java
│   │   ├── FacebookReaction.java
│   │   ├── FacebookReactionSummary.java
│   │   ├── FacebookReview.java
│   │   ├── FacebookSyncResult.java
│   │   └── FacebookUser.java
│   └── instagram/
│       ├── InstagramAttachment.java
│       ├── InstagramComment.java
│       ├── InstagramCommentAnalysis.java
│       ├── InstagramConversation.java
│       ├── InstagramMedia.java
│       ├── InstagramMediaAnalysis.java
│       ├── InstagramMessage.java
│       ├── InstagramSyncResult.java
│       └── InstagramUser.java
├── sentiment/VaderAnalyzer.java
├── service/
│   ├── DataExportService.java
│   └── SentimentSyncService.java
└── web/SyncApiController.java

src/main/resources/
├── application.properties.template
├── vader_lexicon.txt
└── static/index.html
```
