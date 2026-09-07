# Facebook Page Negative Sentiment Analyzer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a modular Java 21 application to retrieve posts and comments from a Facebook Page using the Graph API, analyze negative sentiment locally via VADER, and generate a standalone HTML dashboard.

**Architecture:** A lightweight Java CLI reading from Facebook Graph API (or cached mock JSON), parsing into immutable record models, scoring sentiment via an in-memory VADER lexicon analyzer, and outputting a self-contained interactive `dashboard.html`.

**Tech Stack:** Java 21, Maven, Jackson 2.17+ (JSON), JUnit 5 + AssertJ, VADER Sentiment Lexicon, Chart.js (CDN in generated HTML).

## Global Constraints

- Java 21 source and target compatibility.
- Zero heavyweight frameworks (no Spring Boot). Native `java.net.http.HttpClient`.
- API keys/tokens must never be committed; `config.properties` must be in `.gitignore`.
- Offline mock mode must be fully functional out-of-the-box so the app runs without active Facebook API tokens.

---

### Task 1: Project Scaffolding & Configuration

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `../../../src/main/resources/config.properties`
- Create: `src/main/java/com/fbanalyzer/config/AppConfig.java`
- Test: `src/test/java/com/fbanalyzer/config/AppConfigTest.java`

**Interfaces:**
- Produces: `AppConfig(String pageId, String accessToken, String apiVersion, boolean offlineMode, double negativeThreshold)`

- [ ] **Step 1: Create `pom.xml` with dependencies**
Define Maven project with Java 21, Jackson, JUnit 5, and AssertJ.

- [ ] **Step 2: Create `.gitignore`**
Exclude `.idea/`, `target/`, `output/`, `config.properties`, and sensitive files.

- [ ] **Step 3: Write failing unit test for `AppConfig`**
Verify default fallback values and properties loading.

- [ ] **Step 4: Implement `AppConfig`**
Loads from properties file with fallback to environment variables and sensible defaults.

- [ ] **Step 5: Run test to verify it passes**

---

### Task 2: Data Models (Records)

**Files:**
- Create: `src/main/java/com/fbanalyzer/model/SentimentLevel.java`
- Create: `src/main/java/com/fbanalyzer/model/SentimentScore.java`
- Create: `src/main/java/com/fbanalyzer/model/FacebookComment.java`
- Create: `src/main/java/com/fbanalyzer/model/FacebookPost.java`
- Create: `src/main/java/com/fbanalyzer/model/AnalyzedComment.java`
- Test: `src/test/java/com/fbanalyzer/model/ModelTest.java`

**Interfaces:**
- Produces: `SentimentLevel` (CRITICAL_NEGATIVE, WARNING_NEGATIVE, NEUTRAL, POSITIVE)
- Produces: `FacebookPost`, `FacebookComment`, `SentimentScore`, `AnalyzedComment`

- [ ] **Step 1: Write model tests**
Test record creation, field access, and immutability.

- [ ] **Step 2: Implement records and enum**
Implement Java records for Facebook data and sentiment output.

- [ ] **Step 3: Run model tests to verify they pass**

---

### Task 3: VADER Sentiment Engine

**Files:**
- Create: `src/main/resources/vader_lexicon.txt`
- Create: `src/main/java/com/fbanalyzer/sentiment/VaderAnalyzer.java`
- Test: `src/test/java/com/fbanalyzer/sentiment/VaderAnalyzerTest.java`

**Interfaces:**
- Produces: `VaderAnalyzer.analyze(String text) -> SentimentScore`

- [ ] **Step 1: Place `vader_lexicon.txt` in resources**
Populate standard VADER word-valence dictionary (~7,500 words).

- [ ] **Step 2: Write tests for `VaderAnalyzer`**
Test positive comments ("I love this service!"), negative comments ("This is the worst experience, completely broken!"), punctuation boost ("bad!"), and negation ("not bad").

- [ ] **Step 3: Implement `VaderAnalyzer`**
Lexicon loader, tokenization, valence modifiers (negation, caps, exclamation), and compound normalization.

- [ ] **Step 4: Run tests to verify all sentiment rules pass**

---

### Task 4: Facebook Graph API Client & Mock Data

**Files:**
- Create: `data/sample_feed.json`
- Create: `src/main/java/com/fbanalyzer/client/FacebookClient.java`
- Test: `src/test/java/com/fbanalyzer/client/FacebookClientTest.java`

**Interfaces:**
- Produces: `FacebookClient.fetchPageFeed() -> List<FacebookPost>`

- [ ] **Step 1: Create `data/sample_feed.json`**
Provide rich sample Facebook feed with mixed positive, neutral, and negative comments for testing.

- [ ] **Step 2: Write test for `FacebookClient`**
Verify parsing of mock JSON and error handling on invalid responses.

- [ ] **Step 3: Implement `FacebookClient`**
Uses `HttpClient` when `offlineMode=false`, reads `data/sample_feed.json` when `offlineMode=true`.

- [ ] **Step 4: Run test to verify parsing passes**

---

### Task 5: HTML Dashboard Generator

**Files:**
- Create: `src/main/java/com/fbanalyzer/report/HtmlDashboardGenerator.java`
- Test: `src/test/java/com/fbanalyzer/report/HtmlDashboardGeneratorTest.java`

**Interfaces:**
- Produces: `HtmlDashboardGenerator.generateReport(List<FacebookPost>, List<AnalyzedComment>, Path outputPath)`

- [ ] **Step 1: Write test for `HtmlDashboardGenerator`**
Test that generated HTML contains KPI cards, Chart.js configuration, and negative comment rows.

- [ ] **Step 2: Implement `HtmlDashboardGenerator`**
Build modern responsive HTML with KPI summary cards, Chart.js doughnut chart, severity pills, and search/filter JS.

- [ ] **Step 3: Run test to verify HTML output generation passes**

---

### Task 6: Application Orchestration & End-to-End Verification

**Files:**
- Create: `src/main/java/com/fbanalyzer/App.java`
- Create: `README.md`
- Test: `src/test/java/com/fbanalyzer/AppE2ETest.java`

- [ ] **Step 1: Write end-to-end integration test**
Run entire pipeline in offline mode and assert `output/dashboard.html` is generated with expected negative sentiment count.

- [ ] **Step 2: Implement `App.java`**
Wire configuration, client fetch, sentiment analysis, console summary table, and dashboard generation.

- [ ] **Step 3: Run full Maven build and verification**
Execute full test suite and run the CLI.

- [ ] **Step 4: Write `README.md`**
Clear setup guide for running offline, generating Facebook Page Tokens, and viewing the report.
