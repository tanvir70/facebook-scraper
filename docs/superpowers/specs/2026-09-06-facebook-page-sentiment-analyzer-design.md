# Facebook Page Negative Sentiment Analyzer Design Specification

**Date:** 2026-09-06  
**Status:** Approved  
**Language/Platform:** Java 21, Maven  

---

## 1. Goal & Overview

Build a lightweight, modular Java 21 application that retrieves posts and comments from the user's Facebook Page using the official Facebook Graph API, evaluates sentiment using an embedded VADER (Valence Aware Dictionary and sEntiment Reasoner) engine, and generates a standalone, interactive HTML dashboard highlighting negative sentiment and customer complaints.

---

## 2. Architecture & Modules

The application is structured into four decoupled layers:

```
[Facebook Graph API] / [Local Mock JSON]
                 │
                 ▼
     [com.fbanalyzer.client]
         FacebookClient
                 │
                 ▼ (FacebookPost, FacebookComment records)
   [com.fbanalyzer.sentiment]
          VaderAnalyzer
                 │
                 ▼ (Enriched with SentimentScore)
    [com.fbanalyzer.report]
     HtmlDashboardGenerator
                 │
                 ▼
      output/dashboard.html
```

### Module Responsibilities:
1. **`com.fbanalyzer.config`**:
   - `AppConfig`: Reads configuration parameters from `config.properties` or environment variables (`FB_PAGE_ID`, `FB_PAGE_ACCESS_TOKEN`, `FB_API_VERSION`, `APP_OFFLINE_MODE`, `APP_NEGATIVE_THRESHOLD`).
2. **`com.fbanalyzer.model`**:
   - Immutable records:
     - `FacebookComment(String id, String message, Instant createdTime)`
     - `FacebookPost(String id, String message, Instant createdTime, List<FacebookComment> comments)`
     - `SentimentScore(double compound, double pos, double neu, double neg, SentimentLevel level)`
     - `AnalyzedComment(FacebookComment comment, String postId, String postSnippet, SentimentScore score)`
3. **`com.fbanalyzer.client`**:
   - `FacebookClient`: Uses Java's native `java.net.http.HttpClient` with Jackson.
   - Handles Facebook Graph API pagination and query endpoints:
     `GET /{PAGE_ID}/feed?fields=id,message,created_time,comments{id,message,created_time}&limit=25`
   - **Offline / Mock Mode**: When `APP_OFFLINE_MODE=true` (or when offline), loads mock data from `data/sample_feed.json` so the app runs without live credentials.
4. **`com.fbanalyzer.sentiment`**:
   - `VaderAnalyzer`: Loads `vader_lexicon.txt` (~7,500 scored tokens) into memory at startup.
   - Evaluates text using rule-based scoring:
     - All-caps multiplier (+0.733 boost).
     - Punctuation amplifier (exclamation marks `!`).
     - Negation reversal (*"not bad"*, *"hardly great"*).
     - Degree modifiers (*"extremely"*, *"slightly"*).
     - Normalizes compound score into range `[-1.0, 1.0]`.
   - Classifies levels:
     - `CRITICAL_NEGATIVE`: compound $\le -0.50$
     - `WARNING_NEGATIVE`: $-0.50 <$ compound $\le -0.05$
     - `NEUTRAL`: $-0.05 <$ compound $< 0.05$
     - `POSITIVE`: compound $\ge 0.05$
5. **`com.fbanalyzer.report`**:
   - `HtmlDashboardGenerator`: Builds a standalone `output/dashboard.html` without external web frameworks.
   - Embeds Chart.js via CDN for sentiment distribution (Positive, Neutral, Negative doughnut chart).
   - Generates summary KPI cards: Total Posts, Total Comments, Negative Ratio (%), Health Status.
   - Renders a responsive table of negative comments with filter/search capability.

---

## 3. Data Flow & Testing Strategy

1. **Unit Testing:**
   - `VaderAnalyzerTest`: Verifies known benchmark phrases (positive, negative, negation handling, punctuation boosters).
   - `FacebookClientTest`: Verifies Jackson JSON deserialization against sample Facebook Graph API payloads.
2. **Integration / End-to-End Testing:**
   - Run `App.main` in offline mode to generate `output/dashboard.html`.
   - Verify that all negative comments are captured, scored accurately, and rendered in the HTML table.

---

## 4. Location & Build Setup

- Project root: `/home/tanvirar/Documents/antigravity/hopeful-bose` (IntelliJ-ready Maven project, easily opened in IntelliJ IDEA from `/home/tanvirar/IdeaProjects` or directly).
- Build system: Maven (`pom.xml`) with Java 21 compiler settings.
- Version control: Git tracked with `.gitignore` protecting credentials (`config.properties`, `.env`).
