---
applyTo: "**"
---

# Project rules: study-track-api

Repository-specific rules. The shared rules in the other `.github/instructions/` files (managed by
dev-standards) also apply; where this file is more specific, it wins. Edit this file here.

## Layout and reference classes

- Base package `com.studytrack.api`, **packaged by feature**: `status`, `user`, and from later phases
  `subject`, `topic`, `item`, `media`, `progress`, `importer`, `llm`, `stats`, `search`, `catalog`. Each feature holds its
  `*Controller`, `*Service`, `*Repository`, `*Entity` and `*Dtos` classes. Controllers call only
  services of their own or another feature, never repositories.
- Cross-cutting code: `config` (`AppProperties`, `LoggingAspect`, `WebConfig`,
  `OpenApiConfiguration`) and `exception/ApiExceptionHandler` (error mapping).
- Match `SubjectController` / `SubjectService` (CRUD) and `TopicService` (tree, ownership through the
  subject). Tests: `SubjectIntegrationTest`, `TopicIntegrationTest`, `ItemIntegrationTest`,
  `MediaIntegrationTest`, `ProgressIntegrationTest`, `ImportIntegrationTest`, `LlmSettingsIntegrationTest`
  (they create data through the endpoints with `support/StudyApiClient`; import tests build files with
  `support/TestDocuments`), `HtmlSanitizerTest`, `ReviewSchedulerTest`, `StatsServiceTest`,
  `SearchIntegrationTest`, `SnippetBuilderTest`, `DocumentParserTest`, `DocumentChunkerTest`, `AnthropicLlmClientTest` (provider clients with
  `MockRestServiceServer`), `LoggingAspectTest`.
- Ownership: `SubjectService.findOwnedSubject`, `TopicService.findOwnedTopic` and
  `ItemService.findOwnedItem` throw `NoSuchElementException` (404) for records of another user. Other features reach a subject or topic
  through these methods, never through another feature's repository.
- Feature dependencies point one way: `progress` uses `item`, `topic` and `subject`; `stats` uses
  `subject` and `progress`. `subject`, `topic` and `item` never import `progress`: they read progress
  numbers with their own read-only queries over `question_progress`/`topic_progress`, and `ItemStatus` lives
  in `item`. `importer` uses `llm`, `media`, `item`, `topic` and `subject`; `llm` uses none of them.
  `search` uses `item` and `subject`; nothing uses `search`. `catalog` uses `subject`, `topic` and `user`; none of them import `catalog`
  (`subjects.catalog_subject_id` is a plain column of `SubjectEntity`). `content` holds the shared CSV splitter (`CsvRecordSplitter`).
  Keep it that way to avoid circular dependencies.
- `progress/ReviewScheduler` holds the spaced-repetition rule (unit-tested in `ReviewSchedulerTest`);
  change the schedule there only. "Today" comes from `config/StudyCalendar` (`app.time-zone`), never
  from `LocalDate.now()`.
- The project does not use Lombok; write constructors and loggers explicitly.

## Naming exceptions

- JPA entities end in `Entity`: `UserEntity`.
- Use verb + noun method names everywhere (`listSubjects`, `createTopic`), including
  single-resource services.
- DTO records are grouped in one holder class per feature (`StatusDtos.StatusResponse`).
- `LoggingAspect` matches classes by the `Controller` and `Service` name suffix, so keep those
  suffixes. Class and method names appear in log lines; a rename must be mentioned for anyone
  searching logs.

## Catalog

- `catalog_tracks` / `catalog_subjects` / `catalog_topics` are shared, topics only (no questions). Only an admin
  (`users.is_admin`, checked by `CurrentUser.requireAdmin` in `CatalogAdminService`) changes them, through the CSV
  import (`CatalogImportService`: additive, names matched ignoring case, never deletes). `CatalogSeeder` imports
  `src/main/resources/catalog/*.csv` once into an empty catalog; `CatalogIntegrationTest` imports the shipped files
  and fails on any rejected row, so run it after editing them.
- A user "adds" a catalog subject with `CatalogService.enrollInSubject`, which copies the tree into `subjects` and
  `topics`; the copy never follows later catalog changes.

## Users

- Every user-owned table has a `user_id` column from its first migration.
- Until login is added, services take the user from `CurrentUser.getUserId()`, which reads `AppProperties.currentUserId()`
  (`app.current-user-id`, default 1, seeded by V1). Never accept a user id from request bodies or
  query parameters.

## Persistence

- Flyway owns the schema (`src/main/resources/db/migration`); Hibernate only validates it. Add a new
  `V<n>__<description>.sql` for every schema change and never edit an applied one.
- Since V10 every table is in the `study_track` schema (`hibernate.default_schema`); Flyway's history stays in
  `public`. Migrations and native SQL name tables as `study_track.<table>`, and every new table and column gets
  a `COMMENT ON`. Table names say what they hold (`questions`, `question_progress`, `flashcard_reviews`,
  `resume_points`, `import_drafts`); Java class names predate them (`StudyItemEntity` maps `questions`).
- `questions.subject_id` and `questions.user_id` (V12) always equal the topic's subject and its owner. Set them
  only through `StudyItemEntity.placeInTopic`, never separately.
- Every migration that changes a table must be mirrored in `src/test/resources/test-schema.sql`, or
  the H2 tests will not match production. Flyway is off in tests.
- The `study_track` database is on the same local PostgreSQL server as Folio Pro but is a separate
  database; never reference Folio tables.
- H2 has no `TEXT` type that Hibernate validates as `VARCHAR`; mirror PostgreSQL `TEXT` columns as
  `VARCHAR(1000000)` in `test-schema.sql`.
- Stored HTML references images as `<img src="/api/v1/media/{id}">`, never base64.
- Every HTML field is cleaned by `content/HtmlSanitizer` before it is saved; a new HTML field must go
  through it too. Change the allowed tags there (and in the UI's `RichContentComponent`) together.
- Uploaded files go through the `media.MediaStorage` interface (`LocalMediaStorage` today, under
  `app.media.storage-dir`). Delete files only after the transaction commits. Detect image types from
  their bytes (`ImageType`), never from the name; SVG stays rejected.
- Search (`search/ItemSearchRepository`) is native SQL: PostgreSQL uses the generated
  `study_track.questions.search_vector` column (V8), which Hibernate does not map and H2 cannot create, so H2 tests
  run the `LIKE` fallback. A new searchable field means a new migration that redefines `search_vector`;
  check search on PostgreSQL by hand after such a change.
- `media/MediaFileRepository.findUnusedMedia` decides which images the daily cleanup deletes. A new
  place that stores HTML with images (or keeps a `media_files` id) must be added to its `not exists`
  checks, or its images will be deleted.

## Logging

- `LoggingAspect` logs start/completion and duration of public controller and service methods with
  HTTP method and sanitized path; do not add per-method logs.
- `ApiExceptionHandler` logs handled 4xx without payloads and unexpected exceptions with stack traces.
  Unknown paths return 404 (`NoResourceFoundException`), not 500.
- `APP_LOG_LEVEL` sets the `com.studytrack.api` log level; container logs:
  `docker compose logs -f study-track-api`.
- Never log study content, uploaded document text, prompts, LLM answers or LLM API keys. Imports log
  provider, model, chunk counts and token usage per job.

## Integrations

- LLM providers (Anthropic, OpenAI, Groq, Ollama) sit behind `llm.LlmClient` (`AnthropicLlmClient`,
  `OpenAiLlmClient`, `GroqLlmClient` (extends the OpenAI client; waits out 429s), `OllamaLlmClient`), called with Spring `RestClient` built by `RestLlmClientFactory`
  (bounded timeouts); no vendor SDKs. Use `spring-boot-provider-integration`. Clients translate HTTP
  failures into `LlmException` with a short, safe message (`LlmErrors`).
- Which provider a user imports with comes from `llm_settings` (`LlmSettingsService`), falling back to
  `app.llm` (`LlmProperties`). API keys exist only in `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GROQ_API_KEY`; never add a
  key column, accept a key in a request or return one.
- Anthropic defaults: model `claude-opus-5-5`, API version header `2026-10-21`, structured output with
  the extraction JSON Schema, `output_config.effort` from `app.llm.anthropic.effort`, and the server-side
  refusal fallback on the models that accept it. Check current model ids and API details before changing
  these.
- The extraction prompt and JSON Schema live in `importer/ExtractionPrompt` only. Imports run in
  `ImportProcessingService` on the `importTaskExecutor` with LLM calls outside transactions; saving the
  drafts as questions (no review step; duplicates become `DUPLICATE`) is in `ImportService.saveImportDrafts`, called
  through `ImportDraftsReadyEvent` when an import ends. CSV files skip the LLM: `CsvQuestionReader` turns rows (question, answer, optional topic path) into drafts, saved like every import's.
- Tests use `llm/FakeLlmClientFactory` (a `@Primary` test component, so every test context gets it)
  and never call a real provider.

## Docs, API contract and deployment

- Public API docs: OpenAPI annotations, `docs/API_ENDPOINTS.md`, `README.md` and
  `postman/StudyTrack-API.postman_collection.json` (environment
  `postman/StudyTrack-Local.postman_environment.json`).
- Docker Compose (`docker compose up --build -d`, reads `.env`) runs only the app, which connects
  to PostgreSQL on the host through `host.docker.internal:5432`. `k8s/` and
  `scripts/deploy-local-k8s.sh` are optional, Docker Desktop only.
- Secrets come from environment variables or the git-ignored `.env` (copy `.env.example`).

## Before finishing

- [ ] `mvn test` passes.
- [ ] Flyway migration mirrored in `test-schema.sql`.
- [ ] If an endpoint contract changed, `docs/API_ENDPOINTS.md`, the Postman collection and the
      Angular app (`../study-track-ui`, `src/app/core/services/`) are updated.
