# StudyTrack API

Spring Boot (Java 21) back end for **StudyTrack**, a study tracker: keep notes as subjects, topics and
Q&A items with rich content and images, mark topics complete, resume where you left off, review with
flashcards, and import Word/PDF notes that an LLM turns into questions and answers for you to approve.

The Angular front end is [`study-track-ui`](https://github.com/omkarchorghe16/study-track-ui) (port 4201).
The full build plan, schema and endpoint list are in [`docs/BUILD_PLAN.md`](docs/BUILD_PLAN.md).

## Status

| Phase | Scope | State |
|---|---|---|
| 1 | Scaffolding: structure, rules, health, Swagger, Docker, Flyway V1 (`users`) | **done** |
| 2 | Subjects, topics, items (CRUD) | **done** |
| 3 | Media and rich content | **done** |
| 4 | Progress tracking, dashboard numbers, flashcards | **done** |
| 5 | LLM import (Anthropic, OpenAI, Groq, Ollama) and CSV import; questions are saved at once, duplicates skipped | **done** |
| 6 | Search, duplicate check, unused image cleanup (UI: shortcuts, toasts, theme) | **done** |
| 7 | Accounts and deployment | deferred |

There is no login yet. Every request acts as user `1` (`app.current-user-id`), which V1 seeds. Do not
expose this API publicly.

## Endpoints

Base URL `http://localhost:8083`. Details in [`docs/API_ENDPOINTS.md`](docs/API_ENDPOINTS.md).

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/status` | Service name, API version and the current user (also proves the database is reachable) |
| GET | `/api/v1/users/me` | The current user and whether they are a catalog admin |
| GET | `/api/v1/catalog/tracks`, `/catalog/tracks/{id}/subjects`, `/catalog/subjects/{id}/topics` | Browse the shared catalog: tracks, their subjects, a subject's topic tree |
| POST | `/api/v1/catalog/subjects/{id}/enroll` | Copy a catalog subject, its topic tree and its starter questions into the user's own subjects |
| GET | `/api/v1/catalog/template.csv` | Sample catalog CSV (`track,subject,topic_path`) |
| POST | `/api/v1/admin/catalog/import` | Admin only: add tracks, subjects, topics and starter questions from a CSV |
| GET, POST, DELETE | `/api/v1/items/{id}/explanation` | The saved AI explanation of a question (GET never calls the AI); POST returns it or, only when none is saved, writes it with the AI once for all users; DELETE (admin) removes it so it is rewritten |
| GET, POST | `/api/v1/subjects` | List (with topic and item counts) or create subjects |
| GET, PUT, DELETE | `/api/v1/subjects/{id}` | Read, replace or delete a subject (delete cascades) |
| GET | `/api/v1/subjects/{id}/topics` | The subject's topic tree with item counts |
| POST | `/api/v1/topics` | Create a topic (top level or under a parent) |
| GET, PUT, DELETE | `/api/v1/topics/{id}` | Read, replace or delete a topic (delete cascades to subtopics and items) |
| PATCH | `/api/v1/topics/{id}/move` | New parent and sort order |
| GET | `/api/v1/topics/{id}/items?includeSubtopics=&status=&difficulty=&tag=&page=&size=` | One page of a topic's items (with `includeSubtopics=true` also all its subtopics') with the user's status |
| POST | `/api/v1/items` | Create an item |
| POST | `/api/v1/items/duplicate-check` | Items of the subject with the same question (nothing is saved) |
| GET | `/api/v1/items/search?q=&subjectId=&page=&size=` | Full-text search (PostgreSQL `tsvector`, word prefixes) with highlighted snippets |
| GET, PUT, DELETE | `/api/v1/items/{id}` | Read (with previous/next ids), replace or delete an item |
| POST | `/api/v1/media` | Upload an image (multipart `file`, PNG/JPEG/GIF/WebP, ≤5 MB) |
| GET, DELETE | `/api/v1/media/{id}` | Serve or delete an image (`/info` for its details) |
| PATCH | `/api/v1/items/{id}/progress` | Status (not started, learning, done), confidence, bookmark |
| POST, DELETE | `/api/v1/topics/{id}/complete` | Mark or unmark a topic complete |
| POST | `/api/v1/reviews` | Log a flashcard result; the server schedules the next review |
| GET | `/api/v1/reviews/due?subjectId=` | Flashcards due today, then new items |
| GET | `/api/v1/sessions/resume?subjectId=`, PUT `/api/v1/sessions` | Read or save where the user left off |
| GET | `/api/v1/stats/overview` | Dashboard numbers: totals, per subject, due today, streak, 30-day activity |
| POST, GET | `/api/v1/imports` | Start an import (multipart `file` .docx/.pdf/.md/.txt/.csv ≤20 MB + `subjectId`; CSV = question, answer, optional topic, read without an LLM, `202`) or list recent ones |
| GET | `/api/v1/imports/{id}` | Import status and progress (poll while running) |
| GET | `/api/v1/imports/{id}/candidates`, PUT `…/candidates/{cid}` | Review and edit what the LLM found |
| POST | `/api/v1/imports/{id}/approve`, `/reject` | Turn candidates into items (creating topics) or drop them |
| GET, PUT | `/api/v1/settings/llm` | Provider, model, base URL for imports (keys stay in the environment) |
| POST | `/api/v1/settings/llm/test` | Check the connection without generating text |
| GET | `/actuator/health` (`/liveness`, `/readiness`) | Health probes |
| GET | `/swagger-ui.html`, `/v3/api-docs` | Swagger UI and OpenAPI JSON |

### Postman

Import `postman/StudyTrack-API.postman_collection.json` and the environment
`postman/StudyTrack-Local.postman_environment.json`.

## Prerequisites / run

- Java 21, Maven 3.9+
- PostgreSQL on `localhost:5432` (the same server Folio Pro uses; StudyTrack has its own `study_track` database)

```bash
bash scripts/create-local-db.sh      # once: creates role study_track (password admin) and database study_track
mvn spring-boot:run                  # Flyway applies the migrations on start
open http://localhost:8083/swagger-ui.html
```

`scripts/create-local-db.sh` connects as `postgres`, or as your OS user when there is no `postgres`
role (Homebrew PostgreSQL); set `PG_ADMIN_USER`, `POSTGRES_DB`,
`POSTGRES_USER` or `POSTGRES_PASSWORD` to change that.

### Configuration

Local runs (IDE or `mvn spring-boot:run`) also read `KEY=value` lines from a git-ignored `.env` in the
project folder; real environment variables win. Put `ANTHROPIC_API_KEY` there and restart the API.

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/study_track` | JDBC URL |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `study_track` / `admin` | Database login (local only) |
| `SERVER_PORT` | `8083` | HTTP port |
| `FRONTEND_ORIGIN` | `http://localhost:4201` | Allowed CORS origin for `/api/**` |
| `APP_CURRENT_USER_ID` | `1` | User every request acts as until login exists |
| `APP_LOG_LEVEL` | `INFO` | Log level for `com.studytrack.api` |
| `APP_TIME_ZONE` | `America/Chicago` | Zone that decides "today" for due flashcards, streaks and the activity chart (blank = the server's zone) |
| `MEDIA_STORAGE_DIR` | `./data/media` (`/app/data/media` in Docker) | Where uploaded images are stored (git-ignored) |
| `MEDIA_MAX_IMAGE_BYTES` | `5242880` | Largest accepted image (5 MB) |
| `MEDIA_CLEANUP_CRON` / `MEDIA_CLEANUP_GRACE` | `0 30 3 * * *` / `P7D` | When unused images are deleted (`-` = never) and how old they must be |
| `IMPORT_MAX_FILE_BYTES` | `20971520` | Largest document an import accepts (20 MB) |
| `CATALOG_SEED_ON_STARTUP` | `true` | Fills an empty catalog from `src/main/resources/catalog/*.csv` at startup (once) |
| `IMPORT_WORKER_THREADS` | `2` | Imports processed at the same time |
| `LLM_PROVIDER` | `ANTHROPIC` | Default provider until a user saves settings (`ANTHROPIC`, `OPENAI`, `OLLAMA`) |
| `ANTHROPIC_API_KEY` | (none) | Claude API key. **Environment or `.env` only; never commit it.** |
| `ANTHROPIC_MODEL` / `ANTHROPIC_EFFORT` | `claude-opus-5-5` / `medium` | Default Claude model and `output_config.effort` |
| `OPENAI_API_KEY` / `OPENAI_MODEL` | (none) / `gpt-4o-mini` | OpenAI key and default model |
| `GROQ_API_KEY` / `GROQ_MODEL` | (none) / `openai/gpt-oss-120b` | Groq key (free tier at console.groq.com/keys) and default model; the model must support strict JSON Schema output |
| `GROQ_MAX_CHUNK_TOKENS` / `GROQ_MAX_OUTPUT_TOKENS` | `1500` / `4000` | Groq chunk size and output cap, sized for the free tier's ~8,000 tokens per minute; raise them on a paid tier |
| `OLLAMA_BASE_URL` / `OLLAMA_MODEL` | `http://localhost:11434` / `llama3.1` | Local Ollama server and default model |
| `LLM_TIMEOUT` | `PT5M` | Read timeout of one LLM call |
| `LLM_MAX_CHUNK_TOKENS` / `LLM_MAX_CHUNKS_PER_JOB` | `6000` / `200` | Chunk size and the most LLM calls one import may make |

Imports log the provider, model and token counts per job. Document text, prompts, LLM answers and API
keys are never logged.

## Docker Compose

Compose runs only the API; it connects to PostgreSQL on the host through `host.docker.internal`.

```bash
cp .env.example .env                 # set POSTGRES_PASSWORD to a local-only value
mvn -DskipTests package
docker compose up --build -d
docker compose logs -f study-track-api
```

## Optional Docker Desktop Kubernetes

```bash
mvn -DskipTests package
docker build -t study-track-api:dev-1 .
# Put POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD and FRONTEND_ORIGIN in
# application-k8s.env (KEY=value, one per line); this file is ignored by git.
bash scripts/deploy-local-k8s.sh dev-1
```

The script refuses any context other than `docker-desktop` and runs a readiness smoke test on
`http://127.0.0.1:18083`.

## Engineering conventions

- Package by feature under `com.studytrack.api`; inside each feature `Controller → Service → Repository/Entity`.
- Flyway owns the schema; Hibernate only validates. Every migration is mirrored in `src/test/resources/test-schema.sql`.
- `LoggingAspect` logs every public controller and service call with duration; `ApiExceptionHandler` maps errors to a JSON `ApiError`.
- Shared rules come from [dev-standards](https://github.com/omkarchorghe16/dev-standards) (`.github/dev-standards.lock`); see `CLAUDE.md`.

## Tests

```bash
mvn test
```

Tests run on H2 in PostgreSQL mode with `test-schema.sql`; they need no local database or network.
H2 has no `tsvector`, so search tests cover the `LIKE` fallback; check full-text search against PostgreSQL
(`mvn spring-boot:run`, then `GET /api/v1/items/search?q=…`) when changing `V8` or `ItemSearchRepository`.
LLM calls use `FakeLlmClientFactory` (integration tests) and `MockRestServiceServer` (client tests), so
`mvn test` never calls a provider.
