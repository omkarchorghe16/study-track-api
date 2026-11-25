# StudyTrack API — Endpoint Reference

Base URL (local): `http://localhost:8083` · All paths are prefixed with `/api/v1`.
OpenAPI JSON: `/v3/api-docs` · Swagger UI: `/swagger-ui.html` · Health: `/actuator/health`.

This document describes, for every endpoint, its request/response contract, error cases and which
database tables it reads or writes. Endpoints from later phases are listed in `docs/BUILD_PLAN.md`
section 4 and move here as they are built.

---

## 1. Database overview

Schema is managed by Flyway (`src/main/resources/db/migration`, V1–V3). Hibernate runs with
`ddl-auto: validate`, so entities must match these tables exactly.

| Table | Purpose | Key columns | Entity |
|---|---|---|---|
| `users` | People who own StudyTrack data | `id` identity, `display_name` ≤100, `email` ≤255 UNIQUE, `created_at`. V1 seeds user `1` (`Default user`) | `UserEntity` |
| `subjects` | A user's courses or prep tracks | `id`, `user_id` → `users`, `name` ≤120 UNIQUE per user, `description` ≤1000, `icon` ≤16, `sort_order`, `archived`, `created_at`, `updated_at` | `SubjectEntity` |
| `topics` | Topic tree inside a subject | `id`, `subject_id` → `subjects` ON DELETE CASCADE, `parent_id` → `topics` ON DELETE CASCADE (null = top level, never itself), `name` ≤200, `description_html`, `sort_order`, timestamps | `TopicEntity` |
| `study_items` | Questions, notes, examples, diagrams | `id`, `topic_id` → `topics` ON DELETE CASCADE, `item_type` (`QUESTION`/`NOTE`/`EXAMPLE`/`DIAGRAM`), `title` ≤300, `question_html`, `answer_html`, `notes_html`, `answer_plain` (answer text without tags), `difficulty` (`EASY`/`MEDIUM`/`HARD`, default `MEDIUM`), `source` (`MANUAL`/`IMPORT`), `import_job_id` (FK added in V6), `content_hash` (SHA-256 of the normalised question text, or title), `sort_order`, timestamps | `StudyItemEntity` |
| `tags` | A user's tags | `id`, `user_id` → `users`, `name` ≤50 lower-case, UNIQUE(`user_id`,`name`) | `TagEntity` |
| `study_item_tags` | Item ↔ tag links | PK(`item_id`,`tag_id`), both ON DELETE CASCADE | `StudyItemEntity.tags` |

Every lookup checks ownership: a subject, topic or item of another user is reported as `404`.

## 2. Errors

Every handled error returns `ApiError`:

```json
{ "timestamp": "2026-11-25T03:05:06Z", "status": 404, "error": "Not Found",
  "message": "No endpoint matches this path", "details": {} }
```

| Status | When |
|---|---|
| 400 | Validation failure (`details` maps field → message), malformed JSON or parameter, `IllegalArgumentException` |
| 404 | Unknown path, or a referenced record does not exist or belongs to another user |
| 409 | Duplicate subject name for the user (case-insensitive) |
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

## 5. Subjects

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/subjects` | | `200` `SubjectResponse[]`, by `sortOrder` then `name` |
| GET | `/api/v1/subjects/{subjectId}` | | `200` `SubjectResponse` |
| POST | `/api/v1/subjects` | `SubjectRequest` | `201` + `Location`, `SubjectResponse` |
| PUT | `/api/v1/subjects/{subjectId}` | `SubjectRequest` | `200` `SubjectResponse` |
| DELETE | `/api/v1/subjects/{subjectId}` | | `204`; deletes its topics, items and tag links |

`SubjectRequest`: `name` (required, ≤120, trimmed), `description` (≤1000), `icon` (≤16, e.g. an emoji),
`sortOrder` (≥0; create: missing = last, replace: missing = unchanged), `archived` (missing = false).

`SubjectResponse`: `id`, `name`, `description`, `icon`, `sortOrder`, `archived`, `topicCount`, `itemCount`,
`createdAt`, `updatedAt`. Progress percentages arrive in phase 4.

Errors: `400` validation, `404` unknown subject, `409` duplicate name.
Tables: `subjects`; counts read `topics` and `study_items` with two grouped queries.

```bash
curl -X POST localhost:8083/api/v1/subjects -H 'Content-Type: application/json' \
  -d '{"name":"Java Interview","icon":"☕"}'
```

## 6. Topics

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/subjects/{subjectId}/topics` | | `200` `TopicNodeResponse[]` (tree) |
| GET | `/api/v1/topics/{topicId}` | | `200` `TopicResponse` |
| POST | `/api/v1/topics` | `CreateTopicRequest` | `201` + `Location`, `TopicResponse` |
| PUT | `/api/v1/topics/{topicId}` | `ReplaceTopicRequest` | `200` `TopicResponse` |
| PATCH | `/api/v1/topics/{topicId}/move` | `MoveTopicRequest` | `200` `TopicResponse` |
| DELETE | `/api/v1/topics/{topicId}` | | `204`; deletes subtopics and all their items |

- `CreateTopicRequest`: `subjectId` (required), `parentId` (null = top level; must be in the same subject),
  `name` (required, ≤200), `descriptionHtml`, `sortOrder` (missing = after its siblings).
- `ReplaceTopicRequest`: `name`, `descriptionHtml`, `sortOrder` (missing = unchanged). Use move to re-parent.
- `MoveTopicRequest`: `parentId` (null = top level), `sortOrder` (required, ≥0). `400` if the new parent is in
  another subject, is the topic itself or one of its subtopics.
- `TopicNodeResponse`: `id`, `parentId`, `name`, `descriptionHtml`, `sortOrder`, `itemCount` (own items),
  `totalItemCount` (own + all subtopics), `children` (same shape, ordered by `sortOrder` then `id`).

Tables: `topics`; the tree is built from one query plus one grouped count over `study_items`.

## 7. Items

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/topics/{topicId}/items?difficulty=&tag=&page=0&size=20` | | `200` `ItemPageResponse` |
| GET | `/api/v1/items/{itemId}` | | `200` `ItemResponse` |
| POST | `/api/v1/items` | `ItemRequest` | `201` + `Location`, `ItemResponse` |
| PUT | `/api/v1/items/{itemId}` | `ItemRequest` | `200` `ItemResponse` |
| DELETE | `/api/v1/items/{itemId}` | | `204` |

- List: the topic's own items (not subtopics'), by `sortOrder` then `id`. `difficulty` is `EASY`/`MEDIUM`/`HARD`
  (other values `400`), `tag` matches case-insensitively, `page` ≥0, `size` 1–100. The `status` filter arrives
  with progress tracking (phase 4).
- `ItemRequest`: `topicId` (required; a different topic on PUT moves the item), `itemType` (required),
  `title` (required, ≤300), `questionHtml`, `answerHtml`, `notesHtml` (≤500,000 chars each), `difficulty`
  (missing = `MEDIUM`), `tags` (≤20 names, ≤50 chars; trimmed, lower-cased, de-duplicated, created when new),
  `sortOrder` (missing = last on create or when moving, otherwise unchanged).
- `ItemResponse`: all fields above plus `id`, `subjectId`, `source`, sorted `tags`, `previousItemId` and
  `nextItemId` (neighbours in the topic, or absent), `createdAt`, `updatedAt`.
- `ItemPageResponse`: `items` (`id`, `topicId`, `itemType`, `title`, `difficulty`, `tags`, `sortOrder`,
  `hasAnswer`, `updatedAt`), `page`, `size`, `totalItems`, `totalPages`.

On every save the service sets `answer_plain` (answer text without tags, via jsoup) and `content_hash`.
HTML is stored as sent in this phase; phase 3 sanitises it with jsoup on save. Angular sanitises it when
rendering.

Tables: `study_items`, `tags`, `study_item_tags`; tags for a page are loaded in one batch.
