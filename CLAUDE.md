# study-track-api — guide for Claude Code

Spring Boot (Java 21, Maven) API on port **8083** for StudyTrack, a study tracker for subjects, topics
and Q&A items with progress tracking and LLM-assisted imports of Word/PDF notes. Data lives in the
`study_track` PostgreSQL database. The Angular app (`../study-track-ui`, port 4201) calls its
`/api/v1/*` endpoints through its dev proxy.

The build plan (phases, schema, endpoints) is in `docs/BUILD_PLAN.md`. Work one phase at a time.

## Rules

Before any code change, use the `java-coding-standards` skill (`.claude/skills/java-coding-standards/SKILL.md`).
It loads the project rules, which live in `.github/` and are shared with GitHub Copilot:

- `.github/copilot-instructions.md`: repository overview and the list of rule files.
- `.github/instructions/project.instructions.md`: rules for this repository only (feature packages,
  Flyway, current user, docs paths, final checks).
- `.github/instructions/*.instructions.md` (the rest): shared change-hygiene, coding standards,
  naming and readability, architecture, Spring Boot and logging rules.
- `.github/skills/*/SKILL.md`: workflows for REST APIs, JPA, architecture, logging, tests and providers.

### Where to change a rule

- Rules for this repository only: edit `project.instructions.md` (or `copilot-instructions.md`) here.
- Shared rules and skills: every file that starts with a `Managed by dev-standards` comment, plus
  `.claude/skills/java-coding-standards`, is copied from
  https://github.com/omkarchorghe16/dev-standards (cloned at `../dev-standards`) and listed in
  `.github/dev-standards.lock`. Do not edit those copies here. Change them in `../dev-standards`, then
  run `./sync.sh --all` there and commit the result in each repository.
- `../dev-standards/sync.sh --check .` (run from this repository) reports drift.

## Layout

Package by feature under `com.studytrack.api` (`status`, `user`, later `subject`, `topic`, `item`,
`media`, `progress`, `importer`, `llm`, `stats`). Inside each feature: `*Controller` (HTTP) →
`*Service` (use cases, transactions) → `*Repository` / `*Entity` (JPA), with DTOs in `*Dtos`.
Cross-cutting code: `config` (`AppProperties`, `LoggingAspect`, `WebConfig`, OpenAPI) and
`exception/ApiExceptionHandler`.

## Commands

```bash
mvn test                                         # H2 in-memory; Flyway is off in tests
bash scripts/create-local-db.sh                  # once: create the study_track role and database
mvn spring-boot:run                              # http://localhost:8083/swagger-ui.html
docker compose up --build -d                     # app only (PostgreSQL on the host), reads .env
```

## Things to know

- Flyway owns the schema (`src/main/resources/db/migration`) and Hibernate only validates it. Add a
  new `V<n>__*.sql` for every schema change, never edit an applied one, and mirror it in
  `src/test/resources/test-schema.sql`. Tables live in the `study_track` schema (V10): write
  `study_track.<table>` in migrations and native SQL, and add a `COMMENT ON` for new tables and columns.
- Every user-owned table has `user_id`. Until login exists, services use `AppProperties.currentUserId()`
  (default 1, seeded by V1); never trust a user id sent by the client.
- Secrets (database password, `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GROQ_API_KEY`) come from environment
  variables or the git-ignored `.env` (copy `.env.example`). Never commit real values or log them.
