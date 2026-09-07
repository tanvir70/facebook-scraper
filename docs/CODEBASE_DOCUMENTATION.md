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
**Type:** `record AppConfig(String pageId, String accessToken, String apiVersion, boolean offlineMode, double negativeThreshold)`

Represents the immutable runtime configuration of the application.

#### Fields:
- `pageId`: The numeric Facebook Page ID to scrape.
- `accessToken`: The Meta User/Page Access Token with required permissions.
- `apiVersion`: Facebook Graph API version (default `"v26.0"`).
- `offlineMode`: When `true`, uses mock sample feed rather than making live network calls.
- `negativeThreshold`: Compound sentiment score cut-off (default `-0.05`). Any score $\le -0.05$ is flagged as negative.

#### Constants:
- `DEFAULT_API_VERSION = "v26.0"`: Standard supported Graph API version.
- `DEFAULT_OFFLINE_MODE = true`: Safe fallback so the app works without API credentials.
- `DEFAULT_NEGATIVE_THRESHOLD = -0.05`: VADER standard threshold for negative sentiment.

#### Methods:

##### `public static AppConfig load()`
- **Purpose:** Loads configuration safely from file storage or classpath.
- **Line-by-Line Logic:**
  - `Path localFile = Path.of("config.properties");`: Checks the current working directory for `config.properties`.
  - `if (Files.exists(localFile))`: If found, opens a `FileInputStream` and loads key-value pairs into a `Properties` object.
  - `else`: Tries loading `config.properties` from the application classpath (`getResourceAsStream`).
  - Catches and ignores `IOException` to allow falling back gracefully to environment variables or defaults.
  - Calls `fromProperties(props)` and returns the resulting `AppConfig`.

##### `public static AppConfig fromProperties(Properties props)`
- **Purpose:** Extracts individual settings from properties or environment variables.
- **Line-by-Line Logic:**
  - Calls helper `getPropOrEnv` for `pageId`, `accessToken`, `apiVersion`, `offlineMode`, and `negativeThreshold`.
  - Parses boolean flag `offlineMode` via `Boolean.parseBoolean`.
  - Parses threshold via helper `parseDoubleOrDefault`.
  - Constructs and returns `new AppConfig(...)`.

##### `private static String getPropOrEnv(Properties props, String propKey, String envKey, String defaultVal)`
- **Purpose:** Priority-based fallback resolver for string properties.
- **Line-by-Line Logic:**
  1. Checks `props.getProperty(propKey)`. If non-null and non-blank, returns it trimmed.
  2. Checks `System.getenv(envKey)`. If non-null and non-blank, returns it trimmed.
  3. Returns `defaultVal`.

##### `private static double parseDoubleOrDefault(String str, double defaultVal)`
- **Purpose:** Safely converts string to double without crashing on invalid input.
- **Line-by-Line Logic:**
  - Wraps `Double.parseDouble(str)` in a `try-catch` block catching `NumberFormatException`.
  - Returns `defaultVal` if parsing fails.

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
**Purpose:** Connects to Facebook Graph API or loads sample mock data from disk.

#### Fields:
- `config`: Runtime configuration.
- `sampleDataPath`: `Path` to mock JSON (`data/sample_feed.json`).
- `httpClient`: Standard Java 11+ `java.net.http.HttpClient`.
- `objectMapper`: Jackson `ObjectMapper` configured with `JavaTimeModule` for date-time handling.

#### Constructors:
- `FacebookClient(AppConfig config)`: Default constructor pointing to `data/sample_feed.json` and `HttpClient.newHttpClient()`.
- `FacebookClient(AppConfig config, Path sampleDataPath)`: Allows overriding sample path for custom tests.
- `FacebookClient(AppConfig config, Path sampleDataPath, HttpClient httpClient)`: Full dependency-injection constructor enabling unit tests with mock HTTP clients.

#### Methods:

##### `public List<FacebookPost> fetchPageFeed()`
- **Purpose:** Primary entrypoint to retrieve posts and comments.
- **Line-by-Line Logic:**
  - Checks `if (config.offlineMode())`: Logs mock mode and calls `loadSampleFeed()`.
  - Checks if `pageId` or `accessToken` is blank: Logs warning and falls back to `loadSampleFeed()`.
  - Constructs Graph API URL:
    `https://graph.facebook.com/{apiVersion}/{pageId}/feed?fields=id,message,created_time,comments{id,message,created_time}&limit=25`
  - Prepares `HttpRequest` with `Authorization: Bearer <token>` and a 15-second timeout.
  - Calls `httpClient.send(request, HttpResponse.BodyHandlers.ofString())`.
  - If status code is `200`, delegates to `parseFeedJson(response.body())`.
  - Otherwise, throws a `RuntimeException` detailing HTTP error status and body.

##### `public List<FacebookPost> parseFeedJson(String json)`
- **Purpose:** Parses Facebook Graph API JSON tree into domain records.
- **Line-by-Line Logic:**
  - Reads JSON using `objectMapper.readTree(json)`.
  - Traverses the `"data"` array representing posts.
  - For each post node:
    - Extracts `"id"`, `"message"`, and `"created_time"`.
    - Parses timestamp via `parseInstant(...)`.
    - Traverses nested `comments.data` array.
    - Extracts comment `"id"`, `"message"`, `"created_time"` and constructs `FacebookComment`.
    - Constructs `FacebookPost` with list of comments.
  - Returns accumulated `List<FacebookPost>`.

##### `private List<FacebookPost> loadSampleFeed()`
- **Purpose:** Offline file loader.
- **Line-by-Line Logic:**
  - Checks if `sampleDataPath` exists on filesystem; if so, reads string via `Files.readString(sampleDataPath)`.
  - If missing on disk, attempts to load `sample_feed.json` from classpath.
  - Passes content to `parseFeedJson(json)`.

##### `private Instant parseInstant(String text)`
- **Purpose:** Robust ISO date parser.
- **Line-by-Line Logic:**
  - If null or blank, returns `Instant.now()`.
  - First attempts parsing as ISO offset date-time: `OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()`.
  - Falls back to `Instant.parse(text)`.
  - If all fail, defaults safely to `Instant.now()`.

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
