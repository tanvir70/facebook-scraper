# Facebook Scraper

A Java 21 Facebook Page monitoring application. It fetches posts, comments, reactions, ratings, and reviews through the Meta Graph API, analyzes comment and review sentiment with VADER, and presents the results in a local browser dashboard.

## Features

- Cursor pagination for Page posts and reviews.
- Configurable posts per page, comments per post, and maximum pages.
- Total and per-type reactions for posts and comments: `LIKE`, `LOVE`, `CARE`, `HAHA`, `WOW`, `SAD`, and `ANGRY`.
- Page rating and customer review collection.
- Local VADER sentiment analysis for comments and reviews.
- A **Sync now** browser UI at `http://localhost:8080`.
- Separate dashboard tabs for comments, post reactions, and reviews.
- Comment reactions displayed on their individual comment rows.
- Helpful expired-token errors without storing a Facebook password.

## Meta permissions

Generate a Page access token with:

- `pages_show_list`
- `pages_read_engagement`
- `pages_read_user_content`

Open the [Meta Graph API Explorer](https://developers.facebook.com/tools/explorer/), generate a User token, then call:

```text
/me/accounts?fields=id,name,access_token,tasks
```

Copy the Page ID and Page access token returned for the Page. Meta documents `CARE` as a supported reaction type, but some Like metrics may also include Care activity. The dashboard displays the separate CARE value returned by the reactions endpoint.

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

# Posts returned by each feed request
fb.feed.limit=100

# Nested comments returned for each post
fb.comment.limit=100

# Feed/review pages to follow; use 0 for unlimited
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

Click **Sync now** to run the complete collection and sentiment pipeline.

## Dashboard organization

- **Comments:** Default view containing comment text, parent-post context, sentiment, score, timestamp, and that comment's reactions.
- **Post reactions:** Post-level reaction totals and the per-post reaction breakdown.
- **Reviews:** Page recommendations/reviews with ratings and sentiment.

## Pagination

The first request uses `fb.feed.limit` and `fb.comment.limit`. When Meta returns `paging.next`, the collector follows it until there is no next cursor or `fb.max.pages` is reached.

Nested comment pagination is limited to the configured number of comments returned with each post. A separate comment-edge pagination workflow would be required to retrieve more comments than Meta returns in that nested result.

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
├── sentiment/VaderAnalyzer.java
├── service/SentimentSyncService.java
├── report/HtmlDashboardGenerator.java
└── web/LocalWebServer.java

src/main/resources/
├── config.properties.template
├── vader_lexicon.txt
└── web/index.html
```
