# Facebook Scraper

A lightweight Java 21 web application that fetches posts, comments, and their reaction counts from a Facebook Page, evaluates comments locally with VADER sentiment analysis, and displays the results in a browser dashboard.

---

## Features

- **Official Facebook Graph API Integration:** Clean HTTP REST client using native `java.net.http.HttpClient` with Bearer Token authentication.
- **Post and Comment Reaction Analytics:** Fetches total reactions and the `LIKE`, `LOVE`, `CARE`, `HAHA`, `WOW`, `SAD`, and `ANGRY` breakdown for every returned post and comment.
- **Offline / Mock Mode:** Ships with realistic sample data (`data/sample_feed.json`) so you can develop, test, and run the entire pipeline immediately without waiting for Meta Developer App approval.
- **Local VADER NLP Engine:** Fast in-memory sentiment scoring tailored specifically for social media (accounts for all-caps emphasis, exclamation marks `!`, negation reversal, and booster words).
- **Sync Now UI:** Starts a local dashboard at `http://localhost:8080`. The button calls `POST /api/sync`, fetches the latest Page data and reactions, analyzes every returned comment, and refreshes the results without reloading the page.
- **Sentiment Dashboard:** Shows post and comment reaction breakdowns, negative rate, sentiment distribution, sync status, filters, and the analyzed comment list.
- **Small Embedded Server:** Uses the JDK HTTP server and Jackson, with no web framework or frontend build step.
- **IntelliJ IDEA Ready:** Pre-configured with Maven Wrapper (`./mvnw`).

---

## Quick Start (Offline Mode)

You can run the application immediately out-of-the-box using the included sample feed:

```bash
# 1. Compile and run tests
./mvnw clean test

# 2. Package and start the dashboard with sample data
./mvnw package
java -jar target/facebook-scraper-1.0.0-SNAPSHOT.jar --offline
```

Open [http://localhost:8080](http://localhost:8080), then click **Sync now**.

Use a different port with `--port=9090`.

---

## Connecting to Your Live Facebook Page

### Step 1: Create a Meta Developer App
1. Go to the [Meta for Developers Portal](https://developers.facebook.com/).
2. Click **My Apps** → **Create App**.
3. Select the **Manage everything on your Page** use case.

### Step 2: Generate a Page Access Token
1. Open the [Meta Graph API Explorer](https://developers.facebook.com/tools/explorer/).
2. In the **User or Page** dropdown, select your Facebook Page.
3. Ensure the following permissions are granted:
   - `pages_show_list`
   - `pages_read_engagement`
   - `pages_read_user_content`

`pages_read_engagement` is the permission used to read engagement data such as reaction totals on Page posts and comments.

Meta documents `CARE` as a supported reaction type. It also notes that some Like counts can include Care activity, so the dashboard preserves the separate CARE count returned by the API without trying to derive or subtract values.
4. Use the User token to call `/me/accounts?fields=id,name,access_token,tasks`.
5. Copy the Page ID and the **Page Access Token** returned for your Page.

### Step 3: Configure `config.properties`
Copy the template file to `config.properties`:
```bash
cp src/main/resources/config.properties.template config.properties
```

Edit `config.properties`:
```properties
# Your numeric Facebook Page ID (found in Page -> About -> Page transparency)
fb.page.id=123456789012345

# Your generated Page Access Token
fb.access.token=EAAB...your_token_here...

# API version (defaults to v26.0)
fb.api.version=v26.0

# Set offline mode to false for live scraping
app.offline.mode=false

# Flag comments with compound sentiment score <= -0.05
app.negative.threshold=-0.05
```

Start the application and open the dashboard:
```bash
java -jar target/facebook-scraper-1.0.0-SNAPSHOT.jar
```

```text
http://localhost:8080
```

---

## Opening in IntelliJ IDEA

1. Open **IntelliJ IDEA**.
2. Click **Open** (or **File** → **Open...**).
3. Navigate to the project directory.
4. IntelliJ will automatically recognize the `pom.xml` and configure your Java 21 SDK.
5. Run [`com.fbscraper.App.main()`](src/main/java/com/fbscraper/App.java), then open `http://localhost:8080`.

---

## Project Structure

```
facebook-scraper/
├── pom.xml                                   # Maven build configuration (Java 21)
├── README.md                                 # Documentation & usage guide
├── data/
│   └── sample_feed.json                      # Mock post/comment reactions and content
├── src/
│   ├── main/
│   │   ├── java/com/fbscraper/
│   │   │   ├── App.java                      # Local dashboard entry point
│   │   │   ├── config/
│   │   │   │   └── AppConfig.java            # Config loader with env fallbacks
│   │   │   ├── model/
│   │   │   │   ├── FacebookPost.java         # Immutable records
│   │   │   │   ├── FacebookComment.java
│   │   │   │   ├── ReactionSummary.java      # Total and per-type reaction counts
│   │   │   │   ├── PostReactionAnalysis.java # UI reaction result per post
│   │   │   │   ├── SentimentScore.java
│   │   │   │   ├── SentimentLevel.java
│   │   │   │   ├── AnalyzedComment.java
│   │   │   │   ├── CommentAnalysis.java      # UI comment result
│   │   │   │   └── SyncResult.java           # One complete sync result
│   │   │   ├── client/
│   │   │   │   └── FacebookClient.java       # Facebook Graph API & mock loader
│   │   │   ├── sentiment/
│   │   │   │   └── VaderAnalyzer.java        # Local VADER sentiment engine
│   │   │   ├── service/
│   │   │   │   └── SentimentSyncService.java # Fetch-and-analyze pipeline
│   │   │   ├── web/
│   │   │   │   └── LocalWebServer.java        # Dashboard HTTP/API server
│   │   │   └── report/
│   │   │       └── HtmlDashboardGenerator.java # HTML report builder
│   │   └── resources/
│   │       ├── config.properties.template     # Configuration template
│   │       ├── vader_lexicon.txt              # 7,500+ token sentiment lexicon
│   │       └── web/index.html                 # Browser dashboard
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

To run all unit and integration tests:
```bash
./mvnw clean test
```
