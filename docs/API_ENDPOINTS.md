# StudyTrack API — Endpoint Reference

Base URL (local): `http://localhost:8083` · All paths are prefixed with `/api/v1`.
OpenAPI JSON: `/v3/api-docs` · Swagger UI: `/swagger-ui.html` · Health: `/actuator/health`.

This document describes, for every endpoint, its request/response contract, error cases and which
database tables it reads or writes. Endpoints from later phases are listed in `docs/BUILD_PLAN.md`
section 4 and move here as they are built.

---

## 1. Database overview

Schema is managed by Flyway (`src/main/resources/db/migration`, V1). Hibernate runs with
`ddl-auto: validate`, so entities must match these tables exactly.

| Table | Purpose | Key columns | Entity |
|---|---|---|---|
| `users` | People who own StudyTrack data | `id` identity, `display_name` ≤100, `email` ≤255 UNIQUE, `created_at`. V1 seeds user `1` (`Default user`) | `UserEntity` |

## 2. Errors

Every handled error returns `ApiError`:

```json
{ "timestamp": "2026-11-25T03:05:06Z", "status": 404, "error": "Not Found",
  "message": "No endpoint matches this path", "details": {} }
```

| Status | When |
|---|---|
| 400 | Validation failure (`details` maps field → message), malformed JSON or parameter, `IllegalArgumentException` |
| 404 | Unknown path, or a referenced record does not exist |
| 500 | Unexpected error (logged with stack trace; the body never includes it) |

## 3. Status

### `GET /api/v1/status`

Returns the service identity and the user requests act as. Reading the user also proves the
database is reachable and V1 is applied.

Response `200`:

```json
{ "service": "study-track-api", "apiVersion": "v1", "currentUserId": 1, "displayName": "Default user" }
```

Errors: `404` if the configured `app.current-user-id` has no row in `users`.

Tables: reads `users` (`SELECT … FROM users WHERE id = ?`).

## 4. Health (Actuator)

| Path | Purpose |
|---|---|
| `GET /actuator/health` | Overall status (`{"status":"UP","groups":["liveness","readiness"]}`) |
| `GET /actuator/health/liveness` | Liveness probe (used by `k8s/deployment.yaml`) |
| `GET /actuator/health/readiness` | Readiness probe |
