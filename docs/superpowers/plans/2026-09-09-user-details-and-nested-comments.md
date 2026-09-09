# User Details & Nested Comments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collect user details (`id`, `name`) for all interactions (commenters, nested comment replies, post reactors, comment reactors, and reviewers with reaction types), extract 2nd-level nested comment threads, analyze reply sentiment, export them to dedicated JSON files, and visualize them with author badges, reply trees, and reactor lists in the dashboard.

**Architecture:** Extend Meta Graph API queries on `/{page-id}/feed` and `/{page-id}/ratings` to request `from{id,name}`, nested `comments{...}`, `reactions.limit(N){id,name,type}`, and `reviewer{id,name}`. Introduce `FacebookUser` and `FacebookReaction` records. Enrich `FacebookComment`, `FacebookPost`, `FacebookReview`, `CommentAnalysis`, and `PostReactionAnalysis` with backward-compatible constructors. Update `SentimentSyncService` to score nested replies and aggregate reactor lists. Update `DataExportService` to persist user details in JSON exports, and enhance `index.html` with author badges, expandable reactor lists, and indented nested reply threads.

**Tech Stack:** Java 21, Meta Graph API v26.0, Jackson (JSON), VADER Sentiment, HTML5/CSS3/Vanilla JS dashboard, JUnit 5, AssertJ.

## Global Constraints
- Java 21 LTS records and modern standard library.
- Defensively handle nulls and missing fields (e.g. anonymous or deleted users).
- Preserve backwards compatibility with existing record constructors.
- Maintain maximum allowable defaults (`fb.reaction.limit=100`, `fb.nested_comment.limit=100`).
- No external heavy frontend frameworks; keep `index.html` vanilla HTML/CSS/JS.

---

### Task 1: Configuration for Reaction & Nested Comment Limits

**Files:**
- Modify: `src/main/java/com/fbscraper/config/AppConfig.java`
- Modify: `src/main/resources/config.properties.template`
- Modify: `src/main/resources/config.properties`
- Test: `src/test/java/com/fbscraper/config/AppConfigTest.java`

**Interfaces:**
- Consumes: `AppConfig` record and properties loading.
- Produces: `int reactionLimit()`, `int nestedCommentLimit()` with default 100 on `AppConfig`.

- [ ] **Step 1: Write the failing unit tests for new config properties**

Add tests in `src/test/java/com/fbscraper/config/AppConfigTest.java` verifying that `AppConfig.fromProperties()` parses `fb.reaction.limit` and `fb.nested_comment.limit`, falling back to 100.

```java
@Test
void shouldLoadReactionAndNestedCommentLimitsFromProperties() {
    Properties props = new Properties();
    props.setProperty("fb.page.id", "page-123");
    props.setProperty("fb.access.token", "secret-token");
    props.setProperty("fb.reaction.limit", "50");
    props.setProperty("fb.nested_comment.limit", "30");

    AppConfig config = AppConfig.fromProperties(props);
    assertThat(config.reactionLimit()).isEqualTo(50);
    assertThat(config.nestedCommentLimit()).isEqualTo(30);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AppConfigTest`
Expected: Compilation failure because `reactionLimit()` and `nestedCommentLimit()` do not exist.

- [ ] **Step 3: Update `AppConfig.java` and properties files**

Add `reactionLimit` and `nestedCommentLimit` fields to `AppConfig.java` with default values of 100. Update `config.properties.template` and `config.properties`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AppConfigTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fbscraper/config/AppConfig.java src/main/resources/config.properties* src/test/java/com/fbscraper/config/AppConfigTest.java
git commit -m "feat(config): add reaction.limit and nested_comment.limit properties"
```

---

### Task 2: Domain Models for User Details, Reactions, and Nested Comments

**Files:**
- Create: `src/main/java/com/fbscraper/model/FacebookUser.java`
- Create: `src/main/java/com/fbscraper/model/FacebookReaction.java`
- Modify: `src/main/java/com/fbscraper/model/FacebookComment.java`
- Modify: `src/main/java/com/fbscraper/model/FacebookPost.java`
- Modify: `src/main/java/com/fbscraper/model/FacebookReview.java`
- Modify: `src/main/java/com/fbscraper/model/CommentAnalysis.java`
- Modify: `src/main/java/com/fbscraper/model/PostReactionAnalysis.java`
- Test: `src/test/java/com/fbscraper/model/ModelTest.java`

**Interfaces:**
- Consumes: None
- Produces:
  - `FacebookUser(String id, String name)`
  - `FacebookReaction(String id, String name, String type)`
  - `FacebookComment(String id, String message, Instant createdTime, ReactionSummary reactions, FacebookUser from, List<FacebookComment> replies, List<FacebookReaction> userReactions)`
  - `FacebookPost(String id, String message, Instant createdTime, List<FacebookComment> comments, ReactionSummary reactions, List<FacebookReaction> userReactions)`
  - `FacebookReview(Instant createdTime, String recommendationType, String reviewText, int rating, boolean hasReview, FacebookUser reviewer)`
  - `CommentAnalysis(..., FacebookUser from, List<CommentAnalysis> replies, List<FacebookReaction> userReactions)`
  - `PostReactionAnalysis(..., List<FacebookReaction> userReactions)`

- [ ] **Step 1: Write failing tests in `ModelTest.java`**

Verify `FacebookUser`, `FacebookReaction`, `FacebookComment` with nested `replies` and `userReactions`, `FacebookPost` with `userReactions`, and `FacebookReview` with `reviewer`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ModelTest`
Expected: Compilation failure due to missing types and constructors.

- [ ] **Step 3: Implement `FacebookUser`, `FacebookReaction` and update models**

Create `FacebookUser` and `FacebookReaction`. Update `FacebookComment`, `FacebookPost`, `FacebookReview`, `CommentAnalysis`, and `PostReactionAnalysis` with backward-compatible constructors to preserve existing callers.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=ModelTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fbscraper/model/ src/test/java/com/fbscraper/model/ModelTest.java
git commit -m "feat(model): introduce FacebookUser, FacebookReaction, and enrich Comment, Post, and Review models"
```

---

### Task 3: Meta Graph API Client Extraction for User Details, Reactions, and Nested Comments

**Files:**
- Modify: `src/main/java/com/fbscraper/client/FacebookClient.java`
- Test: `src/test/java/com/fbscraper/client/FacebookClientTest.java`

**Interfaces:**
- Consumes: `AppConfig`, `FacebookUser`, `FacebookReaction`, `FacebookComment`, `FacebookPost`, `FacebookReview`.
- Produces: Updated `buildFeedUrl()`, `parseFeedPage()`, `parseComment()`, `parseReactionsList()`, `parseReviewPage()`.

- [ ] **Step 1: Write failing tests in `FacebookClientTest.java`**

Add tests verifying:
1. `buildFeedUrl()` requests `from{id,name}`, nested `comments`, and `reactions.limit(N){id,name,type}`.
2. `parseFeedPage()` extracts commenter `from`, nested replies list with `from`, post `userReactions` list with `type`, and comment `userReactions`.
3. `parseReviewPage()` extracts `reviewer{id,name}`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=FacebookClientTest`
Expected: Tests fail because user details, nested comments, and reactions list are not yet parsed.

- [ ] **Step 3: Update `FacebookClient.java`**

1. Update `buildFeedUrl()` to request `reactions.limit(%d){id,name,type}` on posts, `from{id,name}` and `reactions.limit(%d){id,name,type}` on comments, and nested `comments.limit(%d){id,message,created_time,from{id,name},reactions.limit(%d){id,name,type},%s}`.
2. Update `parseFeedPage()` and create `parseCommentNode()` helper to parse both top-level and nested comments recursively with `from` and `userReactions`.
3. Update `fetchPageReviews()` URL to include `reviewer{id,name}`.
4. Update `parseReviewPage()` to extract `FacebookUser reviewer`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=FacebookClientTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fbscraper/client/FacebookClient.java src/test/java/com/fbscraper/client/FacebookClientTest.java
git commit -m "feat(client): extract user details for comments, replies, post/comment reactions, and reviews"
```

---

### Task 4: Sentiment Analysis & Nested Reply Processing

**Files:**
- Modify: `src/main/java/com/fbscraper/service/SentimentSyncService.java`
- Test: `src/test/java/com/fbscraper/AppE2ETest.java`

**Interfaces:**
- Consumes: `FacebookPost`, `FacebookComment` with nested replies and reactors, `FacebookReview` with reviewer.
- Produces: `CommentAnalysis` with scored replies tree, `PostReactionAnalysis` with reactors list, `AnalyzedReview` with reviewer.

- [ ] **Step 1: Write failing test in `AppE2ETest.java`**

Verify that `SentimentSyncService.sync()` scores nested comments, assigns author details to `CommentAnalysis` and its replies, and attaches `userReactions` to `PostReactionAnalysis`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=AppE2ETest`
Expected: Fails on missing user details or nested reply assertions.

- [ ] **Step 3: Update `SentimentSyncService.java`**

Update `analyzeComment()` to recursively score `comment.replies()`, creating nested `CommentAnalysis` instances, and pass `comment.from()` and `comment.userReactions()`.
Update post reaction mapping to include `post.userReactions()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=AppE2ETest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fbscraper/service/SentimentSyncService.java src/test/java/com/fbscraper/AppE2ETest.java
git commit -m "feat(service): analyze nested comment replies and propagate user interaction details"
```

---

### Task 5: JSON Data Exports for User Details, Reactions, and Nested Comments

**Files:**
- Modify: `src/main/java/com/fbscraper/service/DataExportService.java`
- Test: `src/test/java/com/fbscraper/service/DataExportServiceTest.java`

**Interfaces:**
- Consumes: `SyncResult` containing enriched comments, reviews, and post reactions.
- Produces: `output/comments.json`, `output/post_reactions.json`, `output/reviews.json`, `output/sync-result.json` with user details.

- [ ] **Step 1: Write test in `DataExportServiceTest.java`**

Test that exported `comments.json` contains `from` user details and nested `replies`, `reviews.json` contains `reviewer`, and `post_reactions.json` contains `userReactions`. Test round-trip loading in `loadLatest()`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=DataExportServiceTest`
Expected: Fails on assertions for `from`, `replies`, or `userReactions`.

- [ ] **Step 3: Update `DataExportService.java`**

Enhance `loadComments()` to deserialize `from`, `replies`, and `userReactions`. Ensure `PostReactionsExport` includes `userReactions`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=DataExportServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fbscraper/service/DataExportService.java src/test/java/com/fbscraper/service/DataExportServiceTest.java
git commit -m "feat(export): persist and reload user details, nested replies, and reactor lists in JSON exports"
```

---

### Task 6: Web Dashboard UI for Comments, Nested Replies, Reactors, and Reviewers

**Files:**
- Modify: `src/main/resources/web/index.html`
- Test: `src/test/java/com/fbscraper/web/LocalWebServerTest.java`

**Interfaces:**
- Consumes: JSON payload from `/api/sync` containing `from`, `replies`, `userReactions`, and `reviewer`.
- Produces:
  - Comment author badge (`👤 [Name] (ID: ...)`)
  - Indented nested reply thread tree under comments
  - Expandable post reactor cards with reaction badges (`LIKE`, `LOVE`, etc.)
  - Reviewer name & ID display on reviews table

- [ ] **Step 1: Update `LocalWebServerTest.java`**

Add assertions that the dashboard HTML and sync JSON contain user details, replies, and reactor data.

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=LocalWebServerTest`
Expected: Fails on new assertions.

- [ ] **Step 3: Update `index.html`**

1. In `createRow(comment)`:
   - Render commenter badge with name and ID.
   - If `comment.replies` exist, render a nested replies container with indented cards showing replier name, message, sentiment badge, and timestamp.
   - If `comment.userReactions` exist, display a reactor popup or chip row showing who reacted.
2. In `createReactionRow(post)`:
   - Add an expandable row or toggle button: `View Reactors (N)` showing the list of users with their reaction type icon/badge.
3. In `createReviewRow(analyzedReview)`:
   - Add a reviewer column/badge showing reviewer name and ID.
4. Add clean CSS styling for nested replies, user badges, and reactor chips.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=LocalWebServerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/web/index.html src/test/java/com/fbscraper/web/LocalWebServerTest.java
git commit -m "feat(web): display commenter details, nested reply threads, post reactors, and reviewers in dashboard"
```

---

### Task 7: Full Test Suite Verification & Documentation

**Files:**
- Modify: `README.md`
- Test: All tests (`./mvnw test`)

- [ ] **Step 1: Run full test suite**

Run: `./mvnw test`
Expected: All tests pass (33+ tests).

- [ ] **Step 2: Update README.md**

Document the user details extraction, nested comments, post/comment reactors, configuration properties (`fb.reaction.limit`, `fb.nested_comment.limit`), and privacy constraints.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: update documentation for user details, nested comments, and reaction tracking"
```
