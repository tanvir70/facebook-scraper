# Comprehensive Codebase Documentation: Facebook Scraper

This document provides an exhaustive, method-by-method, and line-by-line architectural breakdown of the **Facebook Scraper** project.

---

## 1. System Architecture & High-Level Flow

The application is structured as a pipeline with four decoupled stages:

```
[config.properties / Env Vars]
             │
             ▼
      ┌──────────────┐
      │  AppConfig   │  (Configuration Loader)
      └──────┬───────┘
             │
             ▼
      ┌──────────────┐
      │FacebookClient│  (Scraper / Ingestion: Live Graph API or Offline Mock)
      └──────┬───────┘
             │  produces List<FacebookPost> & List<FacebookComment>
             ▼
      ┌──────────────┐
      │VaderAnalyzer │  (VADER Lexicon-based Rule-based Sentiment Engine)
      └──────┬───────┘
             │  produces List<AnalyzedComment>
             ▼
  ┌───────────────────────┐
  │ HtmlDashboardGenerator│  (Stand-alone HTML + Chart.js Dashboard)
  └───────────────────────┘
```

### Execution Lifecycle (in `App.main`):
1. **Bootstrapping (`AppConfig.load()`):** Resolves settings with priority: Working directory `config.properties` $\rightarrow$ Classpath resources $\rightarrow$ Environment variables $\rightarrow$ Hardcoded defaults.
2. **Data Ingestion (`FacebookClient.fetchPageFeed()`):** If offline mode is enabled, reads `data/sample_feed.json`. If online mode is enabled, performs an authenticated HTTPS GET request with Bearer authentication to Meta's Graph API.
3. **Sentiment Analysis (`VaderAnalyzer.analyze()`):** Each comment string is tokenized and scored using a 7,500+ token VADER lexicon, accounting for capitalizations, exclamation intensity, negation words ("not", "never"), and booster adverbs ("extremely", "hardly").
4. **Console KPI Summary:** Computes the percentage of negative feedback and prints the top 5 critical negative comments.
5. **Dashboard Generation (`HtmlDashboardGenerator.generateReport()`):** Emits a self-contained HTML file (`output/dashboard.html`) complete with dark-mode styling, Chart.js donut visualizer, and instant JavaScript search filtering.

---

## 2. Component-by-Component Walkthrough

---

### Component 1: `AppConfig.java`
**Package:** `com.fbscraper.config`  
**Type:** `record AppConfig(String pageId, String accessToken, String apiVersion, int feedLimit, int commentLimit, int maxPages, double negativeThreshold)`

Represents the immutable runtime configuration of the application loaded strictly from `config.properties`.

#### Fields:
- `pageId`: The numeric Facebook Page ID to scrape.
- `accessToken`: The Meta User/Page Access Token with required permissions.
- `apiVersion`: Facebook Graph API version (default `"v26.0"`).
- `feedLimit`: Post limit per request (default `100`, Meta maximum).
- `commentLimit`: Comment limit per post (default `100`, Meta maximum).
- `maxPages`: Maximum number of feed pages to paginate through (default `5`; `0` for unlimited).
- `negativeThreshold`: Compound sentiment score cut-off (default `-0.05`). Any score $\le -0.05$ is flagged as negative.

#### Constants:
- `DEFAULT_API_VERSION = "v26.0"`: Standard supported Graph API version.
- `DEFAULT_FEED_LIMIT = 100`: Maximum allowed batch size for posts per feed request.
- `DEFAULT_COMMENT_LIMIT = 100`: Maximum allowed nested comments retrieved per post.
- `DEFAULT_MAX_PAGES = 5`: Default pagination depth safety cap.
- `DEFAULT_NEGATIVE_THRESHOLD = -0.05`: VADER standard threshold for negative sentiment.

#### Methods:

##### `public static AppConfig load()`
- **Purpose:** Loads configuration strictly from `config.properties`.
- **Line-by-Line Logic:**
  - Checks if `config.properties` exists in the current working directory.
  - If not found, checks `src/main/resources/config.properties`.
  - Throws `IllegalStateException` if neither exists, prompting the user to create one.
  - Opens `FileInputStream` directly and loads properties into a `Properties` instance.
  - Delegates to `fromProperties(props)` and returns the resulting `AppConfig`.

##### `public static AppConfig fromProperties(Properties props)`
- **Purpose:** Extracts individual settings from the properties object.
- **Line-by-Line Logic:**
  - Extracts `fb.page.id`, `fb.access.token`, and `fb.api.version`.
  - Parses `fb.feed.limit` via `parseIntOrDefault(..., 100)`.
  - Parses `fb.comment.limit` via `parseIntOrDefault(..., 100)`.
  - Parses `fb.max.pages` via `parseIntOrDefault(..., 5)`.
  - Parses `app.negative.threshold` via `parseDoubleOrDefault(..., -0.05)`.
  - Constructs and returns `new AppConfig(...)`.

---

### Component 2: Domain Models (`com.fbscraper.model`)

All models are implemented as Java Records (immutable, concise, thread-safe).

#### 1. `FacebookComment(String id, String message, Instant createdTime)`
- Represents a single comment under a post.
- Stores comment ID, textual message, and ISO timestamp.

#### 2. `FacebookPost(String id, String message, Instant createdTime, List<FacebookComment> comments)`
- Represents a page post containing a list of comments.
- **Compact Constructor:**
  ```java
  public FacebookPost {
      comments = (comments == null) ? List.of() : List.copyOf(comments);
  }
  ```
  Guarantees defensive copying so the comments list is always non-null and unmodifiable.

#### 3. `SentimentLevel` (Enum)
Categorical classification derived from compound score:
- `CRITICAL_NEGATIVE`: Compound score $\le -0.50$ (severe complaints, refund demands, outages).
- `WARNING_NEGATIVE`: Compound score between $-0.50$ and $-0.05$ (mild frustration, minor delays).
- `NEUTRAL`: Compound score between $-0.05$ and $+0.05$ (queries, neutral statements).
- `POSITIVE`: Compound score $\ge +0.05$ (praise, satisfaction, positive remarks).

#### 4. `SentimentScore(double compound, double positive, double neutral, double negative, SentimentLevel level)`
- Holds the raw sentiment distribution ratios (`positive`, `neutral`, `negative` summing to 1.0) and the normalized `compound` score ($-1.0$ to $+1.0$).
- **Method `isNegative()`:** Returns `true` if `level == CRITICAL_NEGATIVE || level == WARNING_NEGATIVE`.

#### 5. `AnalyzedComment(FacebookComment comment, String postId, String postSnippet, SentimentScore score)`
- An enriched comment linking the original `FacebookComment`, the parent post ID, a truncated preview of the parent post, and the calculated `SentimentScore`.

---

### Component 3: `FacebookClient.java`
**Package:** `com.fbscraper.client`  
**Purpose:** Connects to Facebook Graph API with cursor-based pagination and configurable post/comment limits.

#### Inner Types:
- `public record FeedPage(List<FacebookPost> posts, String nextUrl)`: Encapsulates a batch of parsed posts and the next cursor URL.
- `public interface HttpSender`: Functional interface `send(HttpRequest request)` decoupling HTTP transport for clean testing.

#### Fields:
- `config`: Runtime configuration.
- `httpSender`: Functional HTTP sender using `HttpClient`.
- `objectMapper`: Jackson `ObjectMapper` configured with `JavaTimeModule` for date-time handling.

#### Constructors:
- `FacebookClient(AppConfig config)`: Default constructor using `HttpClient.newHttpClient()`.
- `FacebookClient(AppConfig config, HttpClient httpClient)`: Convenience constructor for custom HTTP clients.
- `FacebookClient(AppConfig config, HttpSender httpSender)`: Full dependency-injection constructor for testing without mocks or reflection.

#### Methods:

##### `public List<FacebookPost> fetchPageFeed()`
- **Purpose:** Primary entrypoint to retrieve posts and comments across multiple pages.
- **Line-by-Line Logic:**
  - Validates `fb.page.id` and `fb.access.token` (throws `IllegalStateException` if missing).
  - Starts with `buildFeedUrl()` containing `limit={feedLimit}` and `comments.limit({commentLimit})`.
  - Loops while `currentUrl != null`:
    1. Sends GET request with `Authorization: Bearer <token>`.
    2. Parses response via `parseFeedPage(response.body())`.
    3. Appends parsed posts to `allPosts`.
    4. Checks `pageCount >= config.maxPages()`: stops if max pages reached.
    5. Updates `currentUrl = feedPage.nextUrl()`.
  - Returns accumulated list of all posts.

##### `public FeedPage parseFeedPage(String json)`
- **Purpose:** Parses Facebook Graph API JSON tree into domain records and extracts next cursor URL.
- **Line-by-Line Logic:**
  - Reads JSON using `objectMapper.readTree(json)`.
  - Traverses the `"data"` array representing posts.
  - Traverses nested `comments.data` array for each post, creating `FacebookComment` objects.
  - Inspects `root.path("paging").path("next")` to obtain the next cursor URL.
  - Returns `new FeedPage(posts, nextUrl)`.

##### `public List<FacebookPost> parseFeedJson(String json)`
- Convenience wrapper delegating to `parseFeedPage(json).posts()`.

##### `public String buildFeedUrl()`
- **Purpose:** URL-encodes parameters and builds the initial Graph API feed query.
- Encodes `comments.limit(%d){id,message,created_time}` and appends `&limit=%d`.

##### `private Instant parseInstant(String text)`
- **Purpose:** Robust ISO date parser with multi-format fallback.

---

### Component 4: `VaderAnalyzer.java`
**Package:** `com.fbscraper.sentiment`  
**Purpose:** Pure Java implementation of VADER (Valence Aware Dictionary and sEntiment Reasoner). VADER is an empirical, lexicon and rule-based sentiment analysis engine specifically optimized for social media text.

#### Constants & Scientific Parameters:
- `ALPHA = 15.0`: Normalization scaling constant in formula:
  $$\text{compound} = \frac{x}{\sqrt{x^2 + \alpha}}$$
- `C_INCR = 0.733`: Empirical boost added/subtracted when a word is in ALL CAPS.
- `B_INCR = 0.293`: Booster increment for intensifiers ("extremely", "totally", "very").
- `B_DECR = -0.293`: Booster decrement for dampeners ("barely", "hardly", "slightly").
- `NEG_SCALAR = -0.74`: Multiplier applied to valence when preceded by negation ("not", "never").
- `WORD_PATTERN`: Regex `[\p{L}\p{N}']+|[\S]` to capture unicode letters/numbers with contractions or punctuation marks.

#### Methods:

##### `public static VaderAnalyzer createDefault()`
- **Purpose:** Factory method that loads `vader_lexicon.txt` (over 7,500 words with valence scores from $-4.0$ to $+4.0$).
- **Line-by-Line Logic:**
  - Opens stream to `vader_lexicon.txt` from classpath.
  - Reads line-by-line using UTF-8 `BufferedReader`.
  - Skips empty lines and comment lines starting with `#`.
  - Splits lines by tab `\t+`: token is index 0, score is index 1.
  - Populates `Map<String, Double> lexicon` with lowercase tokens and parsed float valence.
  - Returns `new VaderAnalyzer(lexicon)`.

##### `public SentimentScore analyze(String text)`
- **Purpose:** Core sentiment computation algorithm.
- **Line-by-Line Logic:**
  1. **Null/Empty Guard:** If text is null or blank, immediately returns neutral score (`compound = 0.0, neu = 1.0`).
  2. **Tokenization:** Splits text into list of tokens via regex matcher.
  3. **Caps Context:** Evaluates `textHasCaps`: true if text has some uppercase letters but is not 100% all uppercase (to distinguish selective emphasis from someone just typing in caps lock).
  4. **Valence Calculation Loop:**
     - Iterates through each token.
     - Checks if `lexicon.containsKey(token.toLowerCase())`.
     - Retrieves base valence score (e.g. "horrible" $\rightarrow -2.5$).
     - **All-Caps Emphasis:** If this specific token is in ALL CAPS (e.g. `"HORRIBLE"`) and `textHasCaps` is true, increases negative valence by `-C_INCR` (or positive by `+C_INCR`).
     - **Context Window (Lookbehind):** Checks preceding 1 to 3 words:
       - **Negation Check:** If any preceding word within 3 tokens is in `negationWords` (e.g., "not good"), multiplies valence by `NEG_SCALAR (-0.74)` and breaks lookbehind.
       - **Booster Check:** If preceding word is in `boosterDict` (e.g., "very horrible"), adds `boost * (1.0 - (distance * 0.1))` with distance dampening.
     - Appends modified valence to `sentiments` list.
  5. **Punctuation Emphasis:**
     - Counts exclamation marks `!` (capped at 4).
     - Calculates punctuation booster: `punctEmph = count * 0.292` (capped at `0.96`).
     - Adds `punctEmph` if overall score is positive, or subtracts it if negative.
  6. **Compound Normalization:**
     - Calculates `normalize(sumValence)` producing normalized score between $-1.0$ and $+1.0$.
  7. **Ratios (pos, neu, neg):**
     - Computes raw sums of positive words ($s > 0.05$), negative words ($s < -0.05$), and neutral words/tokens.
     - Normalizes ratios to sum to 1.0.
  8. **Threshold Categorization:**
     - $\le -0.50 \rightarrow$ `CRITICAL_NEGATIVE`
     - $\le -0.05 \rightarrow$ `WARNING_NEGATIVE`
     - $\ge +0.05 \rightarrow$ `POSITIVE`
     - Otherwise $\rightarrow$ `NEUTRAL`
  9. Returns immutable `SentimentScore`.

---

### Component 5: `HtmlDashboardGenerator.java`
**Package:** `com.fbscraper.report`  
**Purpose:** Produces an interactive HTML5/CSS3 dashboard highlighting negative comments, analytics metrics, and Chart.js visualizations.

#### Key Methods:

##### `public void generateReport(List<FacebookPost> posts, List<AnalyzedComment> analyzedComments, Path outputPath)`
- **Purpose:** Constructs the full HTML DOM and writes it to disk.
- **Line-by-Line Logic:**
  - Computes counts: `totalPosts`, `totalComments`, `positiveCount`, `neutralCount`, `warningCount`, `criticalCount`, `totalNegative`.
  - Calculates percentages: `negativePercent`, `positivePercent`.
  - Sets health status badge:
    - If `negativePercent > 25.0%`: `⚠️ Attention Required` (`badge-danger`).
    - Else if `negativePercent > 10.0%`: `⚡ Needs Monitoring` (`badge-warning`).
    - Else: `✅ Healthy & Positive` (`badge-success`).
  - Appends embedded CSS styling:
    - Slate/Zinc dark mode theme (`--bg-color: #0f172a`, `--card-bg: #1e293b`).
    - Responsive 2-column flex and grid layouts (`@media (max-width: 768px)`).
    - Status badges, tables, and search input box.
  - Builds KPI metric cards: Total Posts, Total Comments, Negative Feedback Rate, Positive Feedback Rate.
  - Builds chart canvas (`<canvas id="sentimentChart"></canvas>`) and summary action points.
  - Builds Table of Flagged Negative Comments:
    - Filters comments with `c.score().isNegative()`.
    - Sorts with most negative first (`Double.compare(a.score().compound(), b.score().compound())`).
    - Renders severity badges (`CRITICAL` in red, `WARNING` in amber), scores, comment texts, parent post snippets, and dates.
  - Inlines JavaScript:
    - Instantiates Chart.js donut chart with Positive (green), Neutral (slate), Negative (red) slices.
    - Implements `filterTable()`: real-time client-side search filtering rows on keystroke (`onkeyup`).
  - Creates parent directories if missing and writes file via `Files.writeString(outputPath, html.toString())`.

##### `private String escapeHtml(String text)`
- **Purpose:** Sanitizes strings to prevent XSS injection.
- **Replacements:**
  - `&` $\rightarrow$ `&amp;`
  - `<` $\rightarrow$ `&lt;`
  - `>` $\rightarrow$ `&gt;`
  - `"` $\rightarrow$ `&quot;`
  - `'` $\rightarrow$ `&#39;`

---

### Component 6: `App.java`
**Package:** `com.fbscraper`  
**Purpose:** Orchestrator CLI entrypoint.

#### `public static void main(String[] args)`
- **Line-by-Line Breakdown:**
  - **Lines 19-21:** Prints banner:
    ```
    ==================================================
                     Facebook Scraper                 
    ==================================================
    ```
  - **Lines 24-27:** Loads config via `AppConfig.load()` and prints current mode (Offline vs Live) and negative threshold.
  - **Lines 30-38:** Instantiates `FacebookClient` and executes `client.fetchPageFeed()`. If no posts returned, prints notice and terminates early.
  - **Lines 41-42:** Initializes VADER sentiment lexicon via `VaderAnalyzer.createDefault()`.
  - **Lines 45-57:** Iterates over each post and nested comments, constructs post snippet, calls `analyzer.analyze(comment.message())`, and stores `AnalyzedComment`.
  - **Lines 60-63:** Filters comments with `score.compound() <= config.negativeThreshold()` and sorts them by severity.
  - **Lines 66-72:** Prints console analysis summary (Total scanned, Flagged count, Negative rate %).
  - **Lines 74-84:** Prints top 5 flagged comments with their severity level, score, and message.
  - **Lines 87-94:** Instantiates `HtmlDashboardGenerator` and generates `output/dashboard.html`. Outputs file path for browser viewing.

---

## 3. Configuration Reference (`config.properties`)

```properties
# Numeric Facebook Page ID (from Page Transparency or Graph API Explorer)
fb.page.id=YOUR_PAGE_ID

# Meta Page Access Token (pages_read_engagement, pages_read_user_content)
fb.access.token=YOUR_ACCESS_TOKEN

# Facebook Graph API Version
fb.api.version=v26.0

# Set to 'true' for offline mock testing using data/sample_feed.json
app.offline.mode=true

# Negative sentiment threshold (default -0.05)
app.negative.threshold=-0.05
```

---

## 4. Test Suite Architecture

The project has 17 automated tests covering all components:
1. `AppConfigTest`: Tests default configurations and property loading.
2. `FacebookClientTest`: Tests JSON parsing against standard feed structures and timestamp parsing.
3. `ModelTest`: Tests immutability and defensive copying of comments list in `FacebookPost`.
4. `VaderAnalyzerTest` (8 tests):
   - Basic positive/negative scoring.
   - Punctuation amplification (`!` boosts intensity).
   - ALL-CAPS amplification (`"GREAT"` vs `"great"`).
   - Negation reversal (`"not good"` flips positive to negative).
   - Booster incrementation (`"extremely bad"` vs `"bad"`).
   - Null and empty input safety.
5. `HtmlDashboardGeneratorTest`: Validates HTML generation, title, KPI presence, and table structure.
6. `AppE2ETest`: End-to-end integration test verifying that running `App.main()` executes the pipeline and creates `output/dashboard.html`.
