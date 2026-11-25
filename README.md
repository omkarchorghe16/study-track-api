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
| 4 | Progress tracking, dashboard numbers, flashcards | next |
| 5 | LLM import (Anthropic, Ollama first) | planned |
| 6 | Search and polish | planned |
| 7 | Accounts and deployment | deferred |

There is no login yet. Every request acts as user `1` (`app.current-user-id`), which V1 seeds. Do not
expose this API publicly.

## Endpoints

Base URL `http://localhost:8083`. Details in [`docs/API_ENDPOINTS.md`](docs/API_ENDPOINTS.md).

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/status` | Service name, API version and the current user (also proves the database is reachable) |
| GET, POST | `/api/v1/subjects` | List (with topic and item counts) or create subjects |
| GET, PUT, DELETE | `/api/v1/subjects/{id}` | Read, replace or delete a subject (delete cascades) |
| GET | `/api/v1/subjects/{id}/topics` | The subject's topic tree with item counts |
| POST | `/api/v1/topics` | Create a topic (top level or under a parent) |
| GET, PUT, DELETE | `/api/v1/topics/{id}` | Read, replace or delete a topic (delete cascades to subtopics and items) |
| PATCH | `/api/v1/topics/{id}/move` | New parent and sort order |
| GET | `/api/v1/topics/{id}/items?difficulty=&tag=&page=&size=` | One page of a topic's items |
| POST | `/api/v1/items` | Create an item |
| GET, PUT, DELETE | `/api/v1/items/{id}` | Read (with previous/next ids), replace or delete an item |
| POST | `/api/v1/media` | Upload an image (multipart `file`, PNG/JPEG/GIF/WebP, ≤5 MB) |
| GET, DELETE | `/api/v1/media/{id}` | Serve or delete an image (`/info` for its details) |
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

`scripts/create-local-db.sh` connects as `postgres` by default; set `PG_ADMIN_USER`, `POSTGRES_DB`,
`POSTGRES_USER` or `POSTGRES_PASSWORD` to change that.

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_URL` | `jdbc:postgresql://localhost:5432/study_track` | JDBC URL |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `study_track` / `admin` | Database login (local only) |
| `SERVER_PORT` | `8083` | HTTP port |
| `FRONTEND_ORIGIN` | `http://localhost:4201` | Allowed CORS origin for `/api/**` |
| `APP_CURRENT_USER_ID` | `1` | User every request acts as until login exists |
| `APP_LOG_LEVEL` | `INFO` | Log level for `com.studytrack.api` |
| `MEDIA_STORAGE_DIR` | `./data/media` (`/app/data/media` in Docker) | Where uploaded images are stored (git-ignored) |
| `MEDIA_MAX_IMAGE_BYTES` | `5242880` | Largest accepted image (5 MB) |

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
