# Comprehensive Codebase Documentation: Facebook Scraper

This document provides an exhaustive, method-by-method, and line-by-line architectural breakdown of the **Facebook Scraper** project.

---

## 1. System Architecture & High-Level Flow

The application is structured as a pipeline with four decoupled stages:

```
[config.properties]
         │
         ▼
  ┌──────────────┐
  │  AppConfig   │  (Configuration Loader)
  └──────┬───────┘
         │
         ▼
  ┌──────────────┐
  │FacebookClient│  (Scraper / Ingestion: Live Meta Graph API)
  └──────┬───────┘
         │  produces:
         │    - List<FacebookPost> & List<FacebookComment>
         │    - PageRatingSummary (overall_star_rating, rating_count)
         │    - List<FacebookReview>
         ▼
  ┌──────────────┐
  │VaderAnalyzer │  (VADER Lexicon-based Rule-based Sentiment Engine)
  └──────┬───────┘
         │  produces:
         │    - List<AnalyzedComment>
         │    - List<AnalyzedReview>
         ▼
  ┌───────────────────────┐
  │ HtmlDashboardGenerator│  (Stand-alone HTML + Chart.js Dashboard)
  └───────────────────────┘
         │
         ▼
  Outputs:
    - output/dashboard.html
    - output/comments.json
    - output/reviews.json
    - output/rating_summary.json
```

### Execution Lifecycle (in `App.main`):
1. **Bootstrapping (`AppConfig.load()`):** Resolves settings strictly from `config.properties` (checked in working directory or fallback `src/main/resources/config.properties`).
2. **Data Ingestion (`FacebookClient`):**
   - **Feed & Comments:** Fetches page feed posts and comments with cursor-based pagination (`fetchPageFeed()`).
   - **Page Rating Summary:** Queries `/{page-id}?fields=overall_star_rating,rating_count` (`fetchPageRatingSummary()`).
   - **Customer Reviews & Recommendations:** Fetches user reviews from `/{page-id}/ratings` with cursor-based pagination (`fetchPageReviews()`).
3. **Sentiment Analysis (`VaderAnalyzer.analyze()`):**
   - Each comment message is tokenized and scored using a 7,500+ token VADER lexicon, taking into account capitalizations, exclamation intensity, negation words, and booster adverbs.
   - Each customer review text is analyzed with the same VADER engine.
4. **5-Star Rating Calculation (`StarRatingBreakdown.compute()`):**
   - Maps each analyzed comment to 1–5 stars based on VADER compound thresholds ($\ge 0.50 \to 5★$, $\ge 0.05 \to 4★$, $\ge -0.05 \to 3★$, $\ge -0.50 \to 2★$, $<-0.50 \to 1★$).
   - Maps each customer review to 1–5 stars (explicit star rating if $>0$, or 5★ for positive recommendations / 1★ for negative recommendations).
   - Computes weighted average rating ($1.0$ to $5.0$) and counts/percentages for each star level ($5★$ down to $1★$).
5. **Data Export:** Serializes analyzed comments to `output/comments.json`, analyzed reviews to `output/reviews.json`, and rating breakdown to `output/rating_summary.json`.
6. **Console KPI Summary:** Prints high-level metrics for posts, comments, 5-star distribution breakdown, page star rating, and review counts.
7. **Dashboard Generation (`HtmlDashboardGenerator.generateReport()`):** Emits a self-contained HTML file (`output/dashboard.html`) complete with dark-mode styling, Chart.js donut visualizer, 5-Star Rating KPI Card, 5-Star horizontal distribution bars, Meta Page Rating card, customer recommendations table, and instant JavaScript search filtering.

---

## 2. Component-by-Component Walkthrough

---

### Component 1: `AppConfig.java`
**Package:** `com.fbscraper.config`  
**Type:** `record AppConfig(String pageId, String accessToken, String apiVersion, int feedLimit, int commentLimit, int maxPages, double negativeThreshold)`

Represents the immutable runtime configuration of the application loaded strictly from `config.properties`.

#### Fields:
- `pageId`: The numeric Facebook Page ID to scrape.
- `accessToken`: The Meta User/Page Access Token with required permissions (`pages_read_engagement`, `pages_read_user_content`).
- `apiVersion`: Facebook Graph API version (default `"v26.0"`).
- `feedLimit`: Post limit per request (default `100`, Meta maximum).
- `commentLimit`: Comment limit per post (default `100`, Meta maximum).
- `maxPages`: Maximum number of feed/review pages to paginate through (default `5`; `0` for unlimited).
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

#### 3. `PageRatingSummary(double overallStarRating, int ratingCount)`
- Encapsulates Facebook Page rating metrics (`overall_star_rating` and `rating_count`).
- **Constants & Helpers:**
  - `EMPTY`: Constant instance `new PageRatingSummary(0.0, 0)`.
  - `hasRatings()`: Returns `true` if `ratingCount > 0 && overallStarRating > 0.0`.

#### 4. `FacebookReview(Instant createdTime, String recommendationType, String reviewText, int rating, boolean hasReview)`
- Represents a customer review or recommendation retrieved from `/{page-id}/ratings`.
- **Helpers:**
  - `isPositiveRecommendation()`: Returns `true` if `recommendationType` equals `"positive"`.
  - `isNegativeRecommendation()`: Returns `true` if `recommendationType` equals `"negative"`.

#### 5. `SentimentLevel` (Enum)
Categorical classification derived from compound score:
- `CRITICAL_NEGATIVE`: Compound score $\le -0.50$ (severe complaints, refund demands, outages).
- `WARNING_NEGATIVE`: Compound score between $-0.50$ and $-0.05$ (mild frustration, minor delays).
- `NEUTRAL`: Compound score between $-0.05$ and $+0.05$ (queries, neutral statements).
- `POSITIVE`: Compound score $\ge +0.05$ (praise, satisfaction, positive remarks).

#### 6. `SentimentScore(double compound, double positive, double neutral, double negative, SentimentLevel level)`
- Holds the raw sentiment distribution ratios (`positive`, `neutral`, `negative` summing to 1.0) and the normalized `compound` score ($-1.0$ to $+1.0$).
- **Method `isNegative()`:** Returns `true` if `level == CRITICAL_NEGATIVE || level == WARNING_NEGATIVE`.
- **Method `starRating()`:** Returns an integer star rating on a 1–5 scale:
  - 5 stars: $compound \ge 0.50$
  - 4 stars: $0.05 \le compound < 0.50$
  - 3 stars: $-0.05 \le compound < 0.05$
  - 2 stars: $-0.50 \le compound < -0.05$
  - 1 star: $compound < -0.50$

#### 7. `AnalyzedComment(FacebookComment comment, String postId, String postSnippet, SentimentScore score)`
- An enriched comment linking the original `FacebookComment`, parent post ID, a truncated preview of parent post, and calculated `SentimentScore`.

#### 8. `AnalyzedReview(FacebookReview review, SentimentScore score)`
- An enriched customer review pairing the raw `FacebookReview` with its VADER `SentimentScore`.

#### 9. `StarRatingBreakdown(int totalCount, int fiveStarCount, int fourStarCount, int threeStarCount, int twoStarCount, int oneStarCount, double averageRating, int totalPositive, int totalNegative)`
- Represents aggregated 5-star rating metrics computed across both comments and customer reviews.
- **Factory Method:**
  - `compute(List<AnalyzedComment> comments, List<AnalyzedReview> reviews)`: Aggregates all feedback, computes weighted average star rating ($1.0$ to $5.0$), tracks individual counts, and total positive/negative counts.
- **Mapping Helpers:**
  - `mapScoreToStars(SentimentScore score)`: Maps sentiment compound score to 1–5 stars.
  - `mapReviewToStars(AnalyzedReview review)`: Maps customer review to 1–5 stars (explicit rating if $>0$, or 5★ for positive recommendation / 1★ for negative).
- **Formatters & Percentages:**
  - `formattedStars()`: Generates graphical star string (e.g., `★★★★☆`).
  - `fiveStarPercent()`, `fourStarPercent()`, `threeStarPercent()`, `twoStarPercent()`, `oneStarPercent()`: Percentage distributions for breakdown bars.

---

### Component 3: `FacebookClient.java`
**Package:** `com.fbscraper.client`  
**Purpose:** Connects to Facebook Graph API with cursor-based pagination, post/comment scraping, and page ratings/reviews extraction.

#### Inner Types:
- `public record FeedPage(List<FacebookPost> posts, String nextUrl)`: Encapsulates a batch of parsed posts and the next cursor URL.
- `public record ReviewPage(List<FacebookReview> reviews, String nextUrl)`: Encapsulates a batch of parsed reviews and the next cursor URL.
- `public interface HttpSender`: Functional interface `send(HttpRequest request)` decoupling HTTP transport for clean testing.

#### Fields:
- `config`: Runtime configuration.
- `httpSender`: Functional HTTP sender using `HttpClient`.
- `objectMapper`: Jackson `ObjectMapper` configured with `JavaTimeModule` for date-time handling.

#### Constructors:
- `FacebookClient(AppConfig config)`: Default constructor using `HttpClient.newHttpClient()`.
- `FacebookClient(AppConfig config, HttpClient httpClient)`: Convenience constructor for custom HTTP clients.
- `FacebookClient(AppConfig config, HttpSender httpSender)`: Full dependency-injection constructor for testing without network calls.

#### Methods:

##### `public List<FacebookPost> fetchPageFeed()`
- **Purpose:** Primary entrypoint to retrieve posts and comments across multiple pages.
- **Logic:**
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
- Parses Facebook Graph API feed JSON tree into domain records and extracts next cursor URL.

##### `public PageRatingSummary fetchPageRatingSummary()`
- **Purpose:** Queries `GET https://graph.facebook.com/{apiVersion}/{pageId}?fields=overall_star_rating,rating_count`.
- **Logic:**
  - Sends authenticated GET request.
  - Extracts `overall_star_rating` and `rating_count`.
  - Returns `PageRatingSummary.EMPTY` gracefully if reviews are disabled or inaccessible on the page.

##### `public List<FacebookReview> fetchPageReviews()`
- **Purpose:** Queries `GET https://graph.facebook.com/{apiVersion}/{pageId}/ratings?fields=created_time,recommendation_type,review_text,rating,has_review&limit=100`.
- **Logic:**
  - Follows `paging.next` cursors up to `config.maxPages()`.
  - Parses each page via `parseReviewPage(json)`.
  - Returns accumulated `List<FacebookReview>`. Gracefully catches HTTP errors (e.g., reviews disabled in page settings) and returns an empty list without aborting execution.

##### `public ReviewPage parseReviewPage(String json)`
- Parses Facebook Graph API `/ratings` JSON tree into `FacebookReview` domain records and extracts `paging.next`.

##### `public String buildFeedUrl()`
- Encodes query parameters and builds the initial Graph API feed query.

##### `private Instant parseInstant(String text)`
- Robust ISO date parser with multi-format fallback.

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
- Factory method that loads `vader_lexicon.txt` (over 7,500 words with valence scores from $-4.0$ to $+4.0$).

##### `public SentimentScore analyze(String text)`
- Core sentiment computation algorithm:
  1. **Null/Empty Guard:** If text is null or blank, immediately returns neutral score (`compound = 0.0, neu = 1.0`).
  2. **Tokenization:** Splits text into list of tokens via regex matcher.
  3. **Caps Context:** Evaluates `textHasCaps`: true if text has some uppercase letters but is not 100% all uppercase.
  4. **Valence Calculation Loop:**
     - Iterates through each token.
     - Retrieves base valence score from lexicon.
     - Adds all-caps boost if selectively capitalized.
     - Checks 3-word lookbehind for negation words and booster adverbs.
  5. **Punctuation Emphasis:** Calculates boost from exclamation marks `!`.
  6. **Compound Normalization:** Calculates $\frac{\text{sum}}{\sqrt{\text{sum}^2 + \alpha}}$.
  7. **Ratios (pos, neu, neg):** Computes normalized proportions summing to 1.0.
  8. **Threshold Categorization:** Classifies into `CRITICAL_NEGATIVE`, `WARNING_NEGATIVE`, `NEUTRAL`, or `POSITIVE`.

---

### Component 5: `HtmlDashboardGenerator.java`
**Package:** `com.fbscraper.report`  
**Purpose:** Produces an interactive HTML5/CSS3 dashboard highlighting negative comments, customer reviews, 5-star rating breakdowns, rating KPIs, and Chart.js visualizations.

#### Key Methods:

##### `public void generateReport(List<FacebookPost> posts, List<AnalyzedComment> comments, PageRatingSummary ratingSummary, List<AnalyzedReview> reviews, Path outputPath)`
##### `public void generateReport(List<FacebookPost> posts, List<AnalyzedComment> comments, PageRatingSummary ratingSummary, List<AnalyzedReview> reviews, StarRatingBreakdown starRating, Path outputPath)`
- Constructs the full HTML DOM and writes it to disk.
- **Metrics Calculated:**
  - `totalPosts`, `totalComments`, `totalReviews`, `positiveCount`, `neutralCount`, `warningCount`, `criticalCount`, `totalNegative`.
  - Negative and positive feedback percentages.
  - Overall 5-Star calculated rating and graphical star string.
  - Meta Page Rating (if available from Graph API).
- **Dashboard Sections:**
  - **KPI Cards:** Total Posts, Total Comments, Customer Reviews, Overall 5-Star Rating (⭐ `4.2 / 5.0`), Meta Official Rating, Positive Feedback Rate %, Negative Feedback Rate %, Health Status badge.
  - **5-Star Rating Breakdown Card:** Horizontal distribution bars (5★ to 1★ counts and percentages) styled after Amazon/Google reviews.
  - **Sentiment Visualizer:** Chart.js donut chart with Positive, Neutral, and Negative slices.
  - **Flagged Negative Comments Table:** Sorted by severity, showing ⭐ star badge, severity badges, parent post context, comment text, timestamp, and live JavaScript search.
  - **Customer Reviews & Recommendations Table:** Displays all customer reviews with ⭐ star badge, recommendation type badges (`👍 Recommends` / `👎 Doesn't Rec.`), review text, and sentiment classification.

---

### Component 6: `App.java`
**Package:** `com.fbscraper`  
**Purpose:** Orchestrator CLI entrypoint.

#### Execution Pipeline:
1. Loads configuration from `config.properties` via `AppConfig.load()`.
2. Fetches page feed posts and comments with `client.fetchPageFeed()`.
3. Fetches overall page rating summary via `client.fetchPageRatingSummary()`.
4. Fetches customer reviews and recommendations via `client.fetchPageReviews()`.
5. Analyzes all comments and review texts with `VaderAnalyzer`.
6. Calculates 5-star rating distribution and weighted score via `StarRatingBreakdown.compute()`.
7. Exports `output/comments.json`, `output/reviews.json`, and `output/rating_summary.json`.
8. Generates `output/dashboard.html`.
9. Prints console KPIs, 5-star distribution breakdown, and top flagged negative feedback.

---

## 3. Configuration Reference (`config.properties`)

```properties
# Numeric Facebook Page ID (from Page Transparency or Graph API Explorer)
fb.page.id=YOUR_PAGE_ID

# Meta Page Access Token (pages_read_engagement, pages_read_user_content)
fb.access.token=YOUR_ACCESS_TOKEN

# Facebook Graph API Version
fb.api.version=v26.0

# Number of posts to fetch per page request (max allowed by Meta is 100)
fb.feed.limit=100

# Number of comments to fetch per post (max allowed by Meta is 100)
fb.comment.limit=100

# Maximum number of pages to paginate through (default 5; set 0 for unlimited)
fb.max.pages=5

# Negative sentiment threshold (default -0.05)
app.negative.threshold=-0.05
```

---

## 4. Test Suite Architecture

The project has **32 automated tests** across 7 test classes with 100% pass rate:
1. `AppConfigTest`: Tests default configurations and property loading from files.
2. `FacebookClientTest`: Tests JSON parsing against feed structures, cursor pagination, `fetchPageRatingSummary()`, and `fetchPageReviews()`.
3. `ModelTest`: Tests immutability and defensive copying of comments, `PageRatingSummary`, `FacebookReview`, and `AnalyzedReview`.
4. `StarRatingBreakdownTest`: Tests 5-star rating computations, distribution percentages, comment compound score mapping, and customer review mappings.
5. `VaderAnalyzerTest` (8 tests): Tests punctuation boost, caps emphasis, negation handling, boosters, empty inputs.
6. `HtmlDashboardGeneratorTest`: Validates HTML generation, KPI cards, 5-star breakdown bars, Page Rating display, and reviews table rendering.
7. `AppE2ETest`: End-to-end integration test verifying that running `App.main()` executes the pipeline and generates `output/dashboard.html`, `output/comments.json`, `output/reviews.json`, and `output/rating_summary.json`.
