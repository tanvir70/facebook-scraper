# Migration to Gradle and Spring Boot 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the Facebook Scraper from Maven + raw Java embedded HttpServer to Gradle (Groovy DSL) and Spring Boot 3.4.3 with embedded Tomcat, Spring MVC, and `@ConfigurationProperties`.

**Architecture:** Replace Maven with a Gradle 8.13 wrapper build. Convert `App.java` to a `@SpringBootApplication`, migrate configuration to `application.properties` with `@ConfigurationProperties(prefix = "fb")`, replace `LocalWebServer` with `SyncApiController` (`@RestController`) and Spring Boot static resource serving (`/static/index.html`), and wire services using Spring dependency injection.

**Tech Stack:** Java 21, Spring Boot 3.4.3 (`spring-boot-starter-web`, `spring-boot-starter-test`), Gradle 8.13 (Groovy DSL), JUnit 5, AssertJ.

## Global Constraints
- Java 21 toolchain (`sourceCompatibility = JavaVersion.VERSION_21`, `targetCompatibility = JavaVersion.VERSION_21`).
- Gradle Groovy DSL (`build.gradle`, `settings.gradle`).
- Full functional parity: UI dashboard served at `/`, sync endpoint at `POST /api/sync`, status at `GET /api/status`.
- Concurrency guard: `POST /api/sync` must return HTTP 409 Conflict if another sync is in progress.
- Decommission Maven completely (`pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`).

---

### Task 1: Gradle Build Infrastructure Setup

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`

**Interfaces:**
- Produces: Working Gradle 8.13 build system capable of compiling Java 21 and running Spring Boot tasks (`bootRun`, `bootJar`, `test`).

- [ ] **Step 1: Set up Gradle wrapper files**

Copy Gradle wrapper from existing local project `/home/tanvirar/IdeaProjects/customer-relationship-management`:
```bash
mkdir -p gradle/wrapper
cp /home/tanvirar/IdeaProjects/customer-relationship-management/gradlew ./
cp /home/tanvirar/IdeaProjects/customer-relationship-management/gradlew.bat ./
cp /home/tanvirar/IdeaProjects/customer-relationship-management/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/
cp /home/tanvirar/IdeaProjects/customer-relationship-management/gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
chmod +x gradlew
```

- [ ] **Step 2: Create `settings.gradle`**

```groovy
rootProject.name = 'facebook-scraper'
```

- [ ] **Step 3: Create `build.gradle`**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.3'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.fbscraper'
version = '1.0.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Verify Gradle wrapper works**

Run: `./gradlew --version`
Expected output: Gradle 8.13 with JVM 25 / Java 21.

- [ ] **Step 5: Commit build setup**

```bash
git add gradlew gradlew.bat gradle/ settings.gradle build.gradle
git commit -m "build: initialize Gradle 8.13 build with Spring Boot 3.4.3 plugin"
```

---

### Task 2: Configuration & Static Resources Migration

**Files:**
- Create: `src/main/resources/static/index.html`
- Delete: `src/main/resources/web/index.html`
- Create: `src/main/resources/application.properties`
- Create: `src/main/resources/application.properties.template`
- Delete: `config.properties`, `config.properties.template`, `src/main/resources/config.properties`, `src/main/resources/config.properties.template`
- Modify: `src/main/java/com/fbscraper/config/AppConfig.java`
- Modify: `src/test/java/com/fbscraper/config/AppConfigTest.java`

**Interfaces:**
- Consumes: `application.properties` prefix `fb`
- Produces: `@ConfigurationProperties(prefix = "fb")` record `AppConfig`

- [ ] **Step 1: Move web dashboard to Spring Boot static resources**

```bash
mkdir -p src/main/resources/static
git mv src/main/resources/web/index.html src/main/resources/static/index.html
```

- [ ] **Step 2: Create `application.properties` and template**

Create `src/main/resources/application.properties.template`:
```properties
server.port=8080

fb.page-id=YOUR_FACEBOOK_PAGE_ID
fb.access-token=YOUR_PAGE_ACCESS_TOKEN
fb.api-version=v26.0
fb.feed-limit=100
fb.comment-limit=100
fb.nested-comment-limit=100
fb.reaction-limit=100
fb.conversation-limit=100
fb.message-limit=100
fb.max-pages=5
fb.negative-threshold=-0.05
```

Create `src/main/resources/application.properties` with the existing local Page ID and Token from `config.properties`.
Remove `config.properties`, `config.properties.template`, `src/main/resources/config.properties`, `src/main/resources/config.properties.template`.

- [ ] **Step 3: Update `AppConfig.java` to `@ConfigurationProperties`**

Annotate `AppConfig` record with `@ConfigurationProperties(prefix = "fb")` and `@DefaultValue`:
```java
package com.fbscraper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "fb")
public record AppConfig(
        String pageId,
        String accessToken,
        @DefaultValue("v26.0") String apiVersion,
        @DefaultValue("100") int feedLimit,
        @DefaultValue("100") int commentLimit,
        @DefaultValue("100") int conversationLimit,
        @DefaultValue("100") int messageLimit,
        @DefaultValue("100") int reactionLimit,
        @DefaultValue("100") int nestedCommentLimit,
        @DefaultValue("5") int maxPages,
        @DefaultValue("-0.05") double negativeThreshold
) {
    public static final String DEFAULT_API_VERSION = "v26.0";
    public static final int DEFAULT_FEED_LIMIT = 100;
    public static final int DEFAULT_COMMENT_LIMIT = 100;
    public static final int DEFAULT_CONVERSATION_LIMIT = 100;
    public static final int DEFAULT_MESSAGE_LIMIT = 100;
    public static final int DEFAULT_REACTION_LIMIT = 100;
    public static final int DEFAULT_NESTED_COMMENT_LIMIT = 100;
    public static final int DEFAULT_MAX_PAGES = 5;
    public static final double DEFAULT_NEGATIVE_THRESHOLD = -0.05;

    public AppConfig(String pageId, String accessToken, String apiVersion, int feedLimit, int commentLimit, int maxPages, double negativeThreshold) {
        this(pageId, accessToken, apiVersion, feedLimit, commentLimit, DEFAULT_CONVERSATION_LIMIT, DEFAULT_MESSAGE_LIMIT, DEFAULT_REACTION_LIMIT, DEFAULT_NESTED_COMMENT_LIMIT, maxPages, negativeThreshold);
    }
}
```

- [ ] **Step 4: Update `AppConfigTest.java`**

Verify record defaults and custom constructor values.

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests AppConfigTest`
Expected output: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit changes**

```bash
git add src/main/resources/ src/main/java/com/fbscraper/config/ src/test/java/com/fbscraper/config/
git commit -m "feat(config): migrate to application.properties and @ConfigurationProperties"
```

---

### Task 3: Spring Dependency Injection & Service Layer Annotation

**Files:**
- Modify: `src/main/java/com/fbscraper/sentiment/VaderAnalyzer.java`
- Modify: `src/main/java/com/fbscraper/client/FacebookClient.java`
- Modify: `src/main/java/com/fbscraper/service/DataExportService.java`
- Modify: `src/main/java/com/fbscraper/service/SentimentSyncService.java`

**Interfaces:**
- Produces: Managed Spring Beans for `FacebookClient`, `VaderAnalyzer`, `DataExportService`, and `SentimentSyncService`.

- [ ] **Step 1: Update `VaderAnalyzer.java`**

Add `@Component` annotation or create a factory bean in a configuration class so `VaderAnalyzer` is managed as a Spring singleton bean created via `VaderAnalyzer.createDefault()`.

- [ ] **Step 2: Update `FacebookClient.java`**

Annotate with `@Component` and constructor injection:
```java
@Component
public class FacebookClient {
    private final AppConfig config;
    private final HttpClient httpClient;

    public FacebookClient(AppConfig config) {
        this(config, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build());
    }
    // existing methods
}
```

- [ ] **Step 3: Update `DataExportService.java`**

Annotate with `@Service`:
```java
@Service
public class DataExportService {
    public DataExportService() {
        this("output");
    }
    public DataExportService(String outputDir) { ... }
}
```

- [ ] **Step 4: Update `SentimentSyncService.java`**

Annotate with `@Service` and Spring constructor injection:
```java
@Service
public class SentimentSyncService {
    private final FacebookClient client;
    private final VaderAnalyzer analyzer;
    private final DataExportService exportService;
    private final AppConfig config;

    public SentimentSyncService(AppConfig config, FacebookClient client, VaderAnalyzer analyzer, DataExportService exportService) {
        this.config = config;
        this.client = client;
        this.analyzer = analyzer;
        this.exportService = exportService;
    }
    // existing sync() and loadPreviousResult() methods
}
```

- [ ] **Step 5: Run existing service and model tests**

Run: `./gradlew test --tests SentimentSyncService* --tests ModelTest --tests VaderAnalyzerTest --tests DataExportServiceTest --tests FacebookClientTest`
Expected output: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit changes**

```bash
git add src/main/java/com/fbscraper/
git commit -m "feat(service): wire core services and client with Spring DI annotations"
```

---

### Task 4: Web Layer Migration (`SyncApiController`) & Entry Point (`App.java`)

**Files:**
- Create: `src/main/java/com/fbscraper/web/SyncApiController.java`
- Modify: `src/main/java/com/fbscraper/App.java`
- Delete: `src/main/java/com/fbscraper/web/LocalWebServer.java`
- Create: `src/test/java/com/fbscraper/web/SyncApiControllerTest.java`
- Delete: `src/test/java/com/fbscraper/web/LocalWebServerTest.java`

**Interfaces:**
- Consumes: `POST /api/sync`, `GET /api/status`
- Produces: Spring MVC REST endpoints with concurrency lock and error handling matching `LocalWebServer`.

- [ ] **Step 1: Create `SyncApiController.java`**

```java
package com.fbscraper.web;

import com.fbscraper.model.SyncResult;
import com.fbscraper.service.SentimentSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api")
public class SyncApiController {

    private final SentimentSyncService syncService;
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private final AtomicReference<SyncResult> latestResult;

    public SyncApiController(SentimentSyncService syncService) {
        this.syncService = syncService;
        this.latestResult = new AtomicReference<>(syncService.loadPreviousResult().orElse(null));
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        if (!syncing.compareAndSet(false, true)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "A synchronization is already running"));
        }
        try {
            SyncResult result = syncService.sync();
            latestResult.set(result);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            String error = (message == null || message.isBlank()) ? "Synchronization failed" : message;
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", error));
        } finally {
            syncing.set(false);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        SyncResult result = latestResult.get();
        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "NOT_SYNCED", "syncing", syncing.get()));
        }
        return ResponseEntity.ok(result);
    }

    public SyncResult getLatestResult() {
        return latestResult.get();
    }
}
```

- [ ] **Step 2: Update `App.java` to Spring Boot Application**

```java
package com.fbscraper;

import com.fbscraper.config.AppConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppConfig.class)
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

- [ ] **Step 3: Remove `LocalWebServer.java`**

```bash
git rm src/main/java/com/fbscraper/web/LocalWebServer.java
```

- [ ] **Step 4: Create `SyncApiControllerTest.java` and remove `LocalWebServerTest.java`**

Create `src/test/java/com/fbscraper/web/SyncApiControllerTest.java`:
Test `POST /api/sync` returning 200 with result, returning 409 when concurrent, returning 502 on exception, and `GET /api/status`.
Remove `src/test/java/com/fbscraper/web/LocalWebServerTest.java`.

- [ ] **Step 5: Run web layer tests**

Run: `./gradlew test --tests SyncApiControllerTest`
Expected output: PASS.

- [ ] **Step 6: Commit changes**

```bash
git add src/main/java/com/fbscraper/ src/test/java/com/fbscraper/
git commit -m "feat(web): replace LocalWebServer with Spring MVC SyncApiController"
```

---

### Task 5: Full Verification, Maven Decommissioning & Documentation

**Files:**
- Modify: `src/test/java/com/fbscraper/AppE2ETest.java`
- Modify: `README.md`
- Delete: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `dependency-reduced-pom.xml`

**Interfaces:**
- Produces: Fully verified project building via `./gradlew build`, running via `./gradlew bootRun`, with zero Maven references.

- [ ] **Step 1: Update `AppE2ETest.java` to `@SpringBootTest`**

Ensure `AppE2ETest` boots the Spring context and verifies end-to-end sync execution with mock client.

- [ ] **Step 2: Run all tests via Gradle**

Run: `./gradlew test`
Expected output: All tests pass.

- [ ] **Step 3: Build Spring Boot runnable JAR**

Run: `./gradlew bootJar`
Expected output: `build/libs/facebook-scraper-1.0.0-SNAPSHOT.jar` successfully built.

- [ ] **Step 4: Remove all Maven files**

```bash
git rm -rf pom.xml mvnw mvnw.cmd .mvn dependency-reduced-pom.xml
```

- [ ] **Step 5: Update `README.md`**

Update build, configuration, and run instructions:
- `./gradlew bootRun`
- `./gradlew test`
- `./gradlew bootJar`
- `application.properties` instead of `config.properties`

- [ ] **Step 6: Commit final migration and docs**

```bash
git add -A
git commit -m "refactor: complete migration to Gradle and Spring Boot 3"
```
