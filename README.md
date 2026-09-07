# Facebook Scraper

A modular Java 21 application designed to fetch posts and comments from Facebook (Page or Graph API), evaluate sentiment locally using an embedded VADER (Valence Aware Dictionary and sEntiment Reasoner) engine, and generate a standalone interactive HTML dashboard highlighting customer feedback and sentiment insights.

---

## Features

- **Official Facebook Graph API Integration:** Clean HTTP REST client using native `java.net.http.HttpClient` with Bearer Token authentication.
- **Offline / Mock Mode:** Ships with realistic sample data (`data/sample_feed.json`) so you can develop, test, and run the entire pipeline immediately without waiting for Meta Developer App approval.
- **Local VADER NLP Engine:** Fast in-memory sentiment scoring tailored specifically for social media (accounts for all-caps emphasis, exclamation marks `!`, negation reversal, and booster words).
- **Interactive HTML Dashboard:** Generates `output/dashboard.html` with:
  - KPI summary cards (Total Posts, Total Comments, Negative Feedback Rate %, Health Status).
  - Visual doughnut chart (powered by Chart.js).
  - Actionable table of flagged negative comments with real-time keyword search.
- **Zero Heavy Frameworks:** Minimal dependencies (Jackson for JSON, JUnit 5 + AssertJ for testing).
- **IntelliJ IDEA Ready:** Pre-configured with Maven Wrapper (`./mvnw`).

---

## Quick Start (Offline Mode)

You can run the application immediately out-of-the-box using the included sample feed:

```bash
# 1. Compile and run tests
./mvnw clean test

# 2. Package and run the application
./mvnw package
java -jar target/facebook-scraper-1.0.0-SNAPSHOT.jar
```

Once executed, open the generated dashboard in your browser:
```bash
xdg-open output/dashboard.html
# or
google-chrome output/dashboard.html
```

---

## Connecting to Your Live Facebook Page

### Step 1: Create a Meta Developer App
1. Go to the [Meta for Developers Portal](https://developers.facebook.com/).
2. Click **My Apps** → **Create App** → Select **Other** → **Business**.
3. Under Add Products, add **Facebook Login for Business** or **Graph API Explorer**.

### Step 2: Generate a Page Access Token
1. Open the [Meta Graph API Explorer](https://developers.facebook.com/tools/explorer/).
2. In the **User or Page** dropdown, select your Facebook Page.
3. Ensure the following permissions are granted:
   - `pages_read_engagement`
   - `pages_read_user_content`
4. Copy the generated **Page Access Token**.

### Step 3: Configure `config.properties`
Copy the template file to `config.properties`:
```bash
cp src/main/resources/config.properties config.properties
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

Now re-run the application:
```bash
java -jar target/facebook-scraper-1.0.0-SNAPSHOT.jar
```

---

## Opening in IntelliJ IDEA

The project is linked to `/home/tanvirar/IdeaProjects/facebook-sentiment-analyzer`:

1. Open **IntelliJ IDEA**.
2. Click **Open** (or **File** → **Open...**).
3. Navigate to the project directory.
4. IntelliJ will automatically recognize the `pom.xml` and configure your Java 21 SDK.
5. You can directly run [`com.fbscraper.App.main()`](src/main/java/com/fbscraper/App.java) with a single click.

---

## Project Structure

```
facebook-scraper/
├── pom.xml                                   # Maven build configuration (Java 21)
├── README.md                                 # Documentation & usage guide
├── config.properties.template                # Configuration template
├── data/
│   └── sample_feed.json                      # Realistic mock feed for local testing
├── src/
│   ├── main/
│   │   ├── java/com/fbscraper/
│   │   │   ├── App.java                      # CLI coordinator & pipeline runner
│   │   │   ├── config/
│   │   │   │   └── AppConfig.java            # Config loader with env fallbacks
│   │   │   ├── model/
│   │   │   │   ├── FacebookPost.java         # Immutable records
│   │   │   │   ├── FacebookComment.java
│   │   │   │   ├── SentimentScore.java
│   │   │   │   ├── SentimentLevel.java
│   │   │   │   └── AnalyzedComment.java
│   │   │   ├── client/
│   │   │   │   └── FacebookClient.java       # Facebook Graph API & mock loader
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

To run all unit and integration tests:
```bash
./mvnw clean test
```
