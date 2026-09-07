# Facebook Scraper

A modular Java 21 application designed to scrape posts and nested comments from Facebook Pages via the Meta Graph API, evaluate sentiment locally using an embedded VADER (Valence Aware Dictionary and sEntiment Reasoner) engine, export structured JSON datasets (`output/comments.json`), and generate a standalone interactive HTML dashboard (`output/dashboard.html`).

---

## Features

- **Official Facebook Graph API Integration:** Clean HTTP REST client using native `java.net.http.HttpClient` with Bearer Token authentication.
- **Cursor-Based Feed Pagination:** Follows Facebook's `paging.next` cursor across multiple pages to fetch historical posts and comments without hitting arbitrary page limits.
- **Granular Limit Control:** Separately configure posts per page (`fb.feed.limit=100`) and nested comments per post (`fb.comment.limit=100`).
- **Direct Configuration:** Reads directly from a single `config.properties` file without complex classpath or environment variable indirections.
- **Local VADER NLP Engine:** Fast in-memory sentiment scoring tailored specifically for social media (accounts for all-caps emphasis, exclamation marks `!`, negation reversal, and booster words).
- **Interactive HTML Dashboard:** Generates `output/dashboard.html` with:
  - KPI summary cards (Total Posts, Total Comments, Negative Feedback Rate %, Health Status).
  - Visual doughnut chart (powered by Chart.js).
  - Actionable table of flagged negative comments with real-time keyword search.
- **Raw JSON Data Export:** Saves analyzed comments to `output/comments.json` for downstream analytics.
- **Zero Heavy Frameworks:** Minimal dependencies (Jackson for JSON, JUnit 5 + AssertJ for testing).
- **IntelliJ IDEA Ready:** Pre-configured with Maven Wrapper (`./mvnw`).

---

## Configuration (`config.properties`)

Create or edit `config.properties` in your project root (or `src/main/resources/config.properties`):

```properties
# Your numeric Facebook Page ID (found in Page -> About -> Page transparency)
fb.page.id=1214847765056124

# Your Page Access Token with pages_read_engagement & pages_read_user_content permissions
fb.access.token=EAAB...your_token_here...

# Graph API Version (defaults to v26.0)
fb.api.version=v26.0

# Number of posts to fetch per page request (max allowed by Meta is 100)
fb.feed.limit=100

# Number of comments to fetch per post (max allowed by Meta is 100)
fb.comment.limit=100

# Maximum number of pages to paginate through (default 5; set 0 for unlimited)
fb.max.pages=5

# Negative sentiment threshold (default -0.05). Any compound score <= this will be flagged
app.negative.threshold=-0.05
```

---

## Quick Start

### 1. Run with Maven Wrapper
```bash
./mvnw compile exec:java -Dexec.mainClass="com.fbscraper.App"
```

### 2. Build and Run Standalone JAR
```bash
# Package the shaded uber-jar
./mvnw clean package

# Run the application
java -jar target/facebook-scraper-1.0.0-SNAPSHOT.jar
```

### 3. View Results
- **HTML Dashboard:**
  ```bash
  google-chrome output/dashboard.html
  # or
  xdg-open output/dashboard.html
  ```
- **Raw Comments JSON:**
  ```bash
  cat output/comments.json
  ```

---

## How Pagination Works

1. **Initial Call**: The scraper requests `https://graph.facebook.com/v26.0/{page-id}/feed?fields=...&limit=100` requesting up to `100` posts and up to `100` comments per post (`comments.limit(100)`).
2. **Cursor Navigation**: When Meta returns a `paging.next` URL containing the cursor (`after=...`), the scraper automatically fetches the next page.
3. **Safety Cap**: The process repeats until all posts are retrieved or the configured `fb.max.pages` limit is reached (default: 5 pages = up to 500 posts).

---

## Project Structure

```
facebook-scraper/
├── pom.xml                                   # Maven build configuration (Java 21)
├── README.md                                 # Documentation & usage guide
├── config.properties                         # Active runtime configuration (gitignored)
├── config.properties.template                # Configuration template
├── src/
│   ├── main/
│   │   ├── java/com/fbscraper/
│   │   │   ├── App.java                      # CLI coordinator & pipeline runner
│   │   │   ├── config/
│   │   │   │   └── AppConfig.java            # Strict config.properties file reader
│   │   │   ├── model/
│   │   │   │   ├── FacebookPost.java         # Immutable records
│   │   │   │   ├── FacebookComment.java
│   │   │   │   ├── SentimentScore.java
│   │   │   │   ├── SentimentLevel.java
│   │   │   │   └── AnalyzedComment.java
│   │   │   ├── client/
│   │   │   │   └── FacebookClient.java       # Graph API client with cursor pagination
│   │   │   ├── sentiment/
│   │   │   │   └── VaderAnalyzer.java        # Local VADER sentiment engine
│   │   │   └── report/
│   │   │       └── HtmlDashboardGenerator.java # HTML report builder
│   │   └── resources/
│   │       └── vader_lexicon.txt             # 7,500+ token sentiment lexicon
│   └── test/
│       └── java/com/fbscraper/
│           ├── AppE2ETest.java               # End-to-end pipeline test
│           ├── client/FacebookClientTest.java
│           ├── config/AppConfigTest.java
│           ├── model/ModelTest.java
│           ├── report/HtmlDashboardGeneratorTest.java
│           └── sentiment/VaderAnalyzerTest.java
```

---

## Running Automated Tests

To run all 20 unit and integration tests:
```bash
./mvnw clean test
```
