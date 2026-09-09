# Design Spec: Migration to Gradle and Spring Boot 3

- **Date**: 2026-09-09
- **Status**: Approved
- **Target**: Spring Boot 3.3.4, Java 21, Gradle (Groovy DSL)

---

## 1. Overview & Objectives

Migrate the Facebook Scraper application from Maven (`pom.xml`) and raw Java standard library embedded HTTP server (`com.sun.net.httpserver.HttpServer`) to an idiomatic Spring Boot 3 application built with Gradle (Groovy DSL).

### Goals:
1. Establish a modern Gradle build system with Gradle Wrapper (`gradlew`, Gradle 8.10+).
2. Migrate build dependencies to Spring Boot 3.3.4 (`spring-boot-starter-web`, `spring-boot-starter-test`).
3. Transition configuration management from custom `config.properties` loading to Spring Boot's `@ConfigurationProperties` and `application.properties`.
4. Replace `LocalWebServer` with Spring MVC (`SyncApiController`) and standard static resource hosting for the dashboard UI.
5. Provide clean Spring dependency injection for `SentimentSyncService`, `FacebookClient`, `VaderAnalyzer`, and `DataExportService`.
6. Maintain full functional parity across all features (inbox, comments, nested replies, reactions, reviews, and sentiment analysis).
7. Fully decommission Maven (`pom.xml`, `mvnw`, etc.).

---

## 2. Build & Packaging Architecture

### Gradle Configuration (`build.gradle`)
- **Plugins**:
  - `id 'java'`
  - `id 'org.springframework.boot' version '3.3.4'`
  - `id 'io.spring.dependency-management' version '1.1.6'`
- **Java Compatibility**:
  ```groovy
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
  ```
- **Dependencies**:
  - `implementation 'org.springframework.boot:spring-boot-starter-web'`
  - `annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'`
  - `testImplementation 'org.springframework.boot:spring-boot-starter-test'`
- **Tasks**:
  - `bootJar`: Packages the executable fat JAR into `build/libs/facebook-scraper-1.0.0-SNAPSHOT.jar`.
  - `bootRun`: Runs the application directly from source.
  - `test`: Executes JUnit 5 test suite using `useJUnitPlatform()`.

---

## 3. Configuration Management

### `application.properties`
Replaces `config.properties`. Located at `src/main/resources/application.properties` and template at `src/main/resources/application.properties.template`:

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

### `AppConfig.java` Record Binding
```java
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
) { ... }
```
Retain backwards-compatible constructors and factory methods for unit testing.

---

## 4. Web Layer & API

### Endpoints
1. `GET /`:
   - Handled automatically by Spring Boot's static resource engine by locating `index.html` under `src/main/resources/static/index.html`.
2. `POST /api/sync`:
   - Executed via `SyncApiController`.
   - Concurrency guard using `AtomicBoolean syncing`.
   - Returns HTTP 409 Conflict if a sync is already running: `{"error": "A synchronization is already running"}`.
   - Returns HTTP 502 Bad Gateway with error details if the sync throws a `RuntimeException`.
   - Returns HTTP 200 OK with the analyzed `SyncResult` JSON upon success.
3. `GET /api/status`:
   - Returns HTTP 200 with the cached `SyncResult`, or `{"status": "NOT_SYNCED", "syncing": false}` if no sync has completed yet.

---

## 5. Spring Bean / Component Architecture

1. **`App.java`**:
   - `@SpringBootApplication`
   - `@EnableConfigurationProperties(AppConfig.class)`
   - `public static void main(String[] args) { SpringApplication.run(App.class, args); }`
2. **`VaderAnalyzer.java`**:
   - Component / Bean factory initializing from `vader_lexicon.txt`.
3. **`FacebookClient.java`**:
   - Spring `@Component` accepting `AppConfig`.
4. **`DataExportService.java`**:
   - Spring `@Service` managing output file serialization.
5. **`SentimentSyncService.java`**:
   - Spring `@Service` orchestrating scraping, sentiment calculation, and data persistence.
6. **`SyncApiController.java`**:
   - Spring `@RestController` at `/api`.

---

## 6. Testing & Validation

1. **Unit Tests**:
   - `AppConfigTest`: Test property binding defaults.
   - `VaderAnalyzerTest`: Core sentiment scoring tests.
   - `ModelTest`: Model serialization and record invariants.
   - `DataExportServiceTest`: File export and re-import tests.
   - `FacebookClientTest`: Graph API mock server tests.
2. **Web / Integration Tests**:
   - `SyncApiControllerTest`: Test `POST /api/sync` (success, 409 conflict, error) and `GET /api/status` using `@WebMvcTest` or `MockMvc`.
   - `AppE2ETest`: Full Spring context boot test with `@SpringBootTest`.

---

## 7. Migration & Cleanup Plan

1. Generate Gradle wrapper and `build.gradle` / `settings.gradle`.
2. Convert static web resources from `src/main/resources/web/` to `src/main/resources/static/`.
3. Create `application.properties` and `application.properties.template`.
4. Refactor `AppConfig`, `App`, and create `SyncApiController`.
5. Annotate services with Spring annotations (`@Service`, `@Component`).
6. Remove `LocalWebServer.java` and replace `LocalWebServerTest` with `SyncApiControllerTest`.
7. Verify all tests with `./gradlew test`.
8. Delete Maven files: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `dependency-reduced-pom.xml`.
9. Update `README.md` with Gradle instructions.
