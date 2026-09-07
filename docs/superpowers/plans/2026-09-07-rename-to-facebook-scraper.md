# Rename to Facebook Scraper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename project and refactor packages from `facebook-sentiment-analyzer` / `com.fbanalyzer` to `facebook-scraper` / `com.fbscraper`.

**Architecture:** Move Java packages via `git mv` under `src/main` and `src/test`, update package headers and imports, update Maven `pom.xml` coordinates and main class definitions, update CLI banner and HTML dashboard title, update README and IDE configuration, and verify build and test passes.

**Tech Stack:** Java 21, Maven 3.9+, JUnit 5, AssertJ.

## Global Constraints

- Root package name: `com.fbscraper`
- ArtifactId: `facebook-scraper`
- Project Name: `Facebook Scraper`
- Jar name: `target/facebook-scraper-1.0.0-SNAPSHOT.jar`
- Java source compatibility: 21

---

### Task 1: Move Java directories and refactor package names & imports

**Files:**
- Modify (via git mv):
  - `src/main/java/com/fbanalyzer/` -> `src/main/java/com/fbscraper/`
  - `src/test/java/com/fbanalyzer/` -> `src/test/java/com/fbscraper/`
- Modify:
  - `src/main/java/com/fbscraper/App.java`
  - `src/main/java/com/fbscraper/client/FacebookClient.java`
  - `src/main/java/com/fbscraper/config/AppConfig.java`
  - `src/main/java/com/fbscraper/model/AnalyzedComment.java`
  - `src/main/java/com/fbscraper/model/FacebookComment.java`
  - `src/main/java/com/fbscraper/model/FacebookPost.java`
  - `src/main/java/com/fbscraper/model/SentimentLevel.java`
  - `src/main/java/com/fbscraper/model/SentimentScore.java`
  - `src/main/java/com/fbscraper/report/HtmlDashboardGenerator.java`
  - `src/main/java/com/fbscraper/sentiment/VaderAnalyzer.java`
  - `src/test/java/com/fbscraper/AppE2ETest.java`
  - `src/test/java/com/fbscraper/client/FacebookClientTest.java`
  - `src/test/java/com/fbscraper/config/AppConfigTest.java`
  - `src/test/java/com/fbscraper/model/ModelTest.java`
  - `src/test/java/com/fbscraper/report/HtmlDashboardGeneratorTest.java`
  - `src/test/java/com/fbscraper/sentiment/VaderAnalyzerTest.java`

- [ ] **Step 1: Move Java directories using git mv**

```bash
git mv src/main/java/com/fbanalyzer src/main/java/com/fbscraper
git mv src/test/java/com/fbanalyzer src/test/java/com/fbscraper
```

- [ ] **Step 2: Replace `com.fbanalyzer` with `com.fbscraper` across all Java files**

Update package statements and imports:
- Replace `package com.fbanalyzer` with `package com.fbscraper`
- Replace `import com.fbanalyzer.` with `import com.fbscraper.`

- [ ] **Step 3: Verify files compile with new package names**

Temporarily update `pom.xml` mainClass if needed or run compile check:
```bash
find src/ -name "*.java" | xargs grep "com.fbanalyzer"
```
Expected: 0 matches found.

- [ ] **Step 4: Commit directory move and package refactoring**

```bash
git add src/
git commit -m "refactor: rename package com.fbanalyzer to com.fbscraper"
```

---

### Task 2: Update Maven `pom.xml`, `.idea/.name`, and remove stale files

**Files:**
- Modify: `pom.xml`
- Create: `.idea/.name`
- Delete: `dependency-reduced-pom.xml`

- [ ] **Step 1: Update `pom.xml` coordinates and configuration**

In `pom.xml`:
- `<groupId>com.fbscraper</groupId>`
- `<artifactId>facebook-scraper</artifactId>`
- `<name>Facebook Scraper</name>`
- `<description>Scrapes posts and comments from a Facebook Page and analyzes sentiment</description>`
- `<mainClass>com.fbscraper.App</mainClass>` (in `maven-jar-plugin` and `maven-shade-plugin`)

- [ ] **Step 2: Remove stale `dependency-reduced-pom.xml`**

```bash
rm -f dependency-reduced-pom.xml
```

- [ ] **Step 3: Set project name in `.idea/.name`**

Write `facebook-scraper` to `.idea/.name`.

- [ ] **Step 4: Commit pom and IDE configuration changes**

```bash
git add pom.xml .idea/.name
git commit -m "chore: update pom.xml artifactId to facebook-scraper and set idea project name"
```

---

### Task 3: Update CLI Banner, Dashboard Title & Tests

**Files:**
- Modify: `src/main/java/com/fbscraper/App.java`
- Modify: `src/main/java/com/fbscraper/report/HtmlDashboardGenerator.java`
- Modify: `src/test/java/com/fbscraper/report/HtmlDashboardGeneratorTest.java`

- [ ] **Step 1: Update CLI Banner in `App.java`**

Change banner line:
```java
        System.out.println("==================================================");
        System.out.println("                 Facebook Scraper                 ");
        System.out.println("==================================================");
```

- [ ] **Step 2: Update Dashboard Titles in `HtmlDashboardGenerator.java`**

Change:
- `<title>Facebook Scraper Dashboard</title>`
- `<h1>Facebook Scraper Dashboard</h1>`

- [ ] **Step 3: Update and run test in `HtmlDashboardGeneratorTest.java`**

Update assertion in `testGenerateReport_CreatesValidHtmlFile` to assert dashboard title contains `Facebook Scraper Dashboard`.

Run:
```bash
./mvnw test -Dtest=HtmlDashboardGeneratorTest
```
Expected: Tests run: 1, Failures: 0, Errors: 0.

- [ ] **Step 4: Run all unit & E2E tests**

Run:
```bash
./mvnw clean test
```
Expected: Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS.

- [ ] **Step 5: Commit UI and title changes**

```bash
git add src/
git commit -m "feat: update CLI and dashboard branding to Facebook Scraper"
```

---

### Task 4: Update Documentation and Build Final Package

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update `README.md`**

Replace:
- `# Facebook Page Negative Sentiment Analyzer` -> `# Facebook Scraper`
- `facebook-sentiment-analyzer-1.0.0-SNAPSHOT.jar` -> `facebook-scraper-1.0.0-SNAPSHOT.jar`
- `com.fbanalyzer.App.main()` -> `com.fbscraper.App.main()`
- Tree diagram paths `com/fbanalyzer/` -> `com/fbscraper/`
- Directory reference `facebook-sentiment-analyzer/` -> `facebook-scraper/`

- [ ] **Step 2: Package the uber-jar and verify execution**

Run:
```bash
./mvnw clean package
java -jar target/facebook-scraper-1.0.0-SNAPSHOT.jar
```
Expected:
- Output shows:
  ```
  ==================================================
                   Facebook Scraper                 
  ==================================================
  ```
- Build success and standalone uber jar `target/facebook-scraper-1.0.0-SNAPSHOT.jar` generated.

- [ ] **Step 3: Commit documentation updates**

```bash
git add README.md
git commit -m "docs: update README with facebook-scraper branding and instructions"
```
