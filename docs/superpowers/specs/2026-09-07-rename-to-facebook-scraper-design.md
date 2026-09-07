# Design Specification: Rename to Facebook Scraper

**Date:** 2026-09-07  
**Status:** Approved by User  

## 1. Objective

Refactor and rename the existing codebase from **Facebook Page Negative Sentiment Analyzer** (`facebook-sentiment-analyzer` / `com.fbanalyzer`) to **Facebook Scraper** (`facebook-scraper` / `com.fbscraper`).

## 2. Scope of Changes

### 2.1 Maven Project Metadata (`pom.xml`)
- **GroupId:** Change `com.fbanalyzer` to `com.fbscraper`.
- **ArtifactId:** Change `facebook-sentiment-analyzer` to `facebook-scraper`.
- **Name:** Change `Facebook Page Negative Sentiment Analyzer` to `Facebook Scraper`.
- **Description:** Update to describe Facebook scraping and sentiment analysis.
- **Main Class:** Update `<mainClass>` in both `maven-jar-plugin` and `maven-shade-plugin` to `com.fbscraper.App`.

### 2.2 Java Packages and Directories
Move directories using Git to preserve file history:
- `src/main/java/com/fbanalyzer` -> `src/main/java/com/fbscraper`
- `src/test/java/com/fbanalyzer` -> `src/test/java/com/fbscraper`

Update package declarations and imports across all source and test classes:
- Main:
  - `com.fbscraper.App`
  - `com.fbscraper.client.FacebookClient`
  - `com.fbscraper.config.AppConfig`
  - `com.fbscraper.model.*` (`FacebookPost`, `FacebookComment`, `SentimentScore`, `SentimentLevel`, `AnalyzedComment`)
  - `com.fbscraper.sentiment.VaderAnalyzer`
  - `com.fbscraper.report.HtmlDashboardGenerator`
- Tests:
  - `com.fbscraper.AppE2ETest`
  - `com.fbscraper.client.FacebookClientTest`
  - `com.fbscraper.config.AppConfigTest`
  - `com.fbscraper.model.ModelTest`
  - `com.fbscraper.sentiment.VaderAnalyzerTest`
  - `com.fbscraper.report.HtmlDashboardGeneratorTest`

### 2.3 User Interface and CLI Display
- **CLI Header (`App.java`):**
  Update ASCII banner to display `Facebook Scraper`.
- **HTML Dashboard (`HtmlDashboardGenerator.java`):**
  Update `<title>` and `<h1>` to `Facebook Scraper Dashboard`.

### 2.4 IDE and Documentation
- **IntelliJ Project Name:** Create/update `.idea/.name` with `facebook-scraper`.
- **README (`README.md`):**
  - Update headings, overview, CLI command examples (`java -jar target/facebook-scraper-1.0.0-SNAPSHOT.jar`).
  - Update directory tree and class references (`com.fbscraper.App.main()`).
- **Clean Generated Artifacts:** Delete stale `dependency-reduced-pom.xml` (regenerated on package build).

## 3. Verification Plan
- Run `./mvnw clean test` to ensure all 17 tests pass with the new package structure.
- Run `./mvnw package -DskipTests` to verify package generation creates `target/facebook-scraper-1.0.0-SNAPSHOT.jar`.
- Execute `./mvnw exec:java -Dexec.mainClass="com.fbscraper.App"` to confirm the application launches and produces `output/dashboard.html` with correct branding.
