# StudyTrack API — Endpoint Reference

Base URL (local): `http://localhost:8083` · All paths are prefixed with `/api/v1`.
OpenAPI JSON: `/v3/api-docs` · Swagger UI: `/swagger-ui.html` · Health: `/actuator/health`.

This document describes, for every endpoint, its request/response contract, error cases and which
database tables it reads or writes. Endpoints from later phases are listed in `docs/BUILD_PLAN.md`
section 4 and move here as they are built.

---

## 1. Database overview

Schema is managed by Flyway (`src/main/resources/db/migration`, V1–V5). Hibernate runs with
`ddl-auto: validate`, so entities must match these tables exactly.

| Table | Purpose | Key columns | Entity |
|---|---|---|---|
| `users` | People who own StudyTrack data | `id` identity, `display_name` ≤100, `email` ≤255 UNIQUE, `created_at`. V1 seeds user `1` (`Default user`) | `UserEntity` |
| `subjects` | A user's courses or prep tracks | `id`, `user_id` → `users`, `name` ≤120 UNIQUE per user, `description` ≤1000, `icon` ≤16, `sort_order`, `archived`, `created_at`, `updated_at` | `SubjectEntity` |
| `topics` | Topic tree inside a subject | `id`, `subject_id` → `subjects` ON DELETE CASCADE, `parent_id` → `topics` ON DELETE CASCADE (null = top level, never itself), `name` ≤200, `description_html`, `sort_order`, timestamps | `TopicEntity` |
| `study_items` | Questions, notes, examples, diagrams | `id`, `topic_id` → `topics` ON DELETE CASCADE, `item_type` (`QUESTION`/`NOTE`/`EXAMPLE`/`DIAGRAM`), `title` ≤300, `question_html`, `answer_html`, `notes_html`, `answer_plain` (answer text without tags), `difficulty` (`EASY`/`MEDIUM`/`HARD`, default `MEDIUM`), `source` (`MANUAL`/`IMPORT`), `import_job_id` (FK added in V6), `content_hash` (SHA-256 of the normalised question text, or title), `sort_order`, timestamps | `StudyItemEntity` |
| `tags` | A user's tags | `id`, `user_id` → `users`, `name` ≤50 lower-case, UNIQUE(`user_id`,`name`) | `TagEntity` |
| `study_item_tags` | Item ↔ tag links | PK(`item_id`,`tag_id`), both ON DELETE CASCADE | `StudyItemEntity.tags` |
| `item_progress` | A user's progress on an item (no row = `NOT_STARTED`) | `id`, `user_id`, `item_id` → `study_items` ON DELETE CASCADE, `status` (`NOT_STARTED`/`LEARNING`/`DONE`), `confidence` 1–5 or null, `review_count`, `last_reviewed_at`, `next_review_at`, `bookmarked`, `updated_at`; UNIQUE(`user_id`,`item_id`) | `ItemProgressEntity` |
| `topic_progress` | Manual "mark complete" | `id`, `user_id`, `topic_id` → `topics` ON DELETE CASCADE, `completed`, `completed_at`; UNIQUE(`user_id`,`topic_id`) | `TopicProgressEntity` |
| `review_log` | One row per flashcard rating | `id`, `user_id`, `item_id` → `study_items` ON DELETE CASCADE, `result` (`AGAIN`/`HARD`/`GOOD`/`EASY`), `reviewed_at` | `ReviewLogEntity` |
| `study_sessions` | "Continue where you left off", per subject | `id`, `user_id`, `subject_id` → `subjects` ON DELETE CASCADE, `last_topic_id` → `topics` ON DELETE SET NULL, `last_item_id` → `study_items` ON DELETE SET NULL, `updated_at`; UNIQUE(`user_id`,`subject_id`) | `StudySessionEntity` |
| `media_files` | Uploaded images | `id`, `user_id` → `users`, `item_id` → `study_items` ON DELETE SET NULL (null = not used by an item yet), `original_name` ≤255, `content_type`, `size_bytes` >0, `storage_key` UNIQUE (`yyyy/MM/<uuid>.<ext>` under `app.media.storage-dir`), `width`, `height`, `alt_text` ≤500, `created_at` | `MediaFileEntity` |

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
| 413 | Upload larger than `app.media.max-image-bytes` (5 MB) or the multipart limit (20 MB) |
| 415 | Upload that is not a PNG, JPEG, GIF or WebP image (checked by content, not by name), or an unsupported request content type |
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

`SubjectResponse`: `id`, `name`, `description`, `icon`, `sortOrder`, `archived`, `topicCount`,
`completedTopicCount` (topics marked complete), `itemCount`, `doneItemCount`, `learningItemCount`,
`percentDone` (DONE / all items, rounded down so 100 means all done; 0 without items), `createdAt`, `updatedAt`.

Errors: `400` validation, `404` unknown subject, `409` duplicate name.
Tables: `subjects`; counts read `topics`, `study_items`, `item_progress` and `topic_progress` with three grouped queries.

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
- `TopicNodeResponse`: `id`, `parentId`, `name`, `descriptionHtml`, `sortOrder`, `itemCount` and `doneCount`
  (own items), `totalItemCount` and `totalDoneCount` (own + all subtopics), `percentDone` (from the totals,
  rounded down), `completed` (the user marked it complete), `allItemsDone` (hint: at least one item and all
  of them DONE), `children` (same shape, ordered by `sortOrder` then `id`).

Tables: `topics`; the tree is built from one query, one grouped count over `study_items` with
`item_progress`, and one read of `topic_progress`.

## 7. Items

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/topics/{topicId}/items?status=&difficulty=&tag=&page=0&size=20` | | `200` `ItemPageResponse` |
| GET | `/api/v1/items/{itemId}` | | `200` `ItemResponse` |
| POST | `/api/v1/items` | `ItemRequest` | `201` + `Location`, `ItemResponse` |
| PUT | `/api/v1/items/{itemId}` | `ItemRequest` | `200` `ItemResponse` |
| DELETE | `/api/v1/items/{itemId}` | | `204` |

- List: the topic's own items (not subtopics'), by `sortOrder` then `id`. `difficulty` is `EASY`/`MEDIUM`/`HARD`
  (other values `400`), `status` is `NOT_STARTED`/`LEARNING`/`DONE` for the current user, `tag` matches
  case-insensitively, `page` ≥0, `size` 1–100.
- `ItemRequest`: `topicId` (required; a different topic on PUT moves the item), `itemType` (required),
  `title` (required, ≤300), `questionHtml`, `answerHtml`, `notesHtml` (≤500,000 chars each), `difficulty`
  (missing = `MEDIUM`), `tags` (≤20 names, ≤50 chars; trimmed, lower-cased, de-duplicated, created when new),
  `sortOrder` (missing = last on create or when moving, otherwise unchanged).
- `ItemResponse`: all fields above plus `id`, `subjectId`, `source`, sorted `tags`, `previousItemId` and
  `nextItemId` (neighbours in the topic, or absent), `progress` (`ItemProgressResponse`, see section 9),
  `createdAt`, `updatedAt`.
- `ItemPageResponse`: `items` (`id`, `topicId`, `itemType`, `title`, `difficulty`, `tags`, `sortOrder`,
  `hasAnswer`, `status`, `bookmarked`, `updatedAt`), `page`, `size`, `totalItems`, `totalPages`.

On every save the service sets `answer_plain` (answer text without tags, via jsoup) and `content_hash`.
Question, answer and notes HTML are sanitised with jsoup on every save (see section 8, Rich content), so
the response can differ from what was sent. Images the item references are linked to it in `media_files`;
images an edit no longer references are unlinked (kept, `item_id` cleared) so they can be used again, and
all of the item's linked images are deleted with their files when the item is deleted.

Tables: `study_items`, `tags`, `study_item_tags`, `item_progress`; tags and progress for a page are loaded in one
batch each.

## 8. Media

| Method | Path | Body | Success |
|---|---|---|---|
| POST | `/api/v1/media` | `multipart/form-data`: `file` (required), `altText` (optional, cut to 500) | `201` + `Location`, `MediaResponse` |
| GET | `/api/v1/media/{mediaId}` | | `200`, the image bytes |
| GET | `/api/v1/media/{mediaId}/info` | | `200` `MediaResponse` |
| DELETE | `/api/v1/media/{mediaId}` | | `204`; removes the row and the file |

- Upload accepts PNG, JPEG, GIF and WebP, detected from the file's first bytes (the name and the declared
  content type are ignored). SVG is rejected because it can carry scripts. Empty file `400`, over
  `app.media.max-image-bytes` (default 5 MB) `413`, any other type `415`. Width and height are read from
  the image header when possible.
- A new upload has no item. It is linked to an item when an item saved with
  `<img src="/api/v1/media/{id}">` in its HTML references it. Images of another user are never linked.
- `GET /{mediaId}` sends `Cache-Control: private, max-age=31536000, immutable`, `X-Content-Type-Options:
  nosniff` and an inline `Content-Disposition` (images never change; a new upload gets a new id).
- `MediaResponse`: `id`, `url` (`/api/v1/media/{id}`, use it as `img src`), `originalName`, `contentType`,
  `sizeBytes`, `width`, `height`, `altText`, `itemId` (absent until linked), `createdAt`.

Files are stored by `LocalMediaStorage` under `app.media.storage-dir` (`MEDIA_STORAGE_DIR`, default
`./data/media`) behind the `MediaStorage` interface, so S3 can replace it later. Files are removed only
after the database transaction commits, and a stored file is removed again if the upload transaction
rolls back.

Tables: `media_files`.

### Rich content

`content/HtmlSanitizer` (jsoup) cleans every stored HTML field: item question, answer and notes, and topic
descriptions.

- Allowed: jsoup's relaxed safelist (paragraphs, headings, lists, tables, `b`/`i`/`u`, `blockquote`,
  `code`, `pre`, links, images) plus `s`, `del`, `ins`, `hr`, `mark`, `figure`, `figcaption`. Kept
  attributes: `class` on `pre`/`code`/`span`, `data-language` and `spellcheck` on `pre`, `data-list` on
  `li` (Quill lists), `type` on `ol`.
- Removed: scripts, styles, event handlers, `javascript:` links, and every `img` whose `src` is not
  `/api/v1/media/{id}` (so no base64 or external images).
- Links get `rel="noopener noreferrer nofollow"`, and `target="_blank"` when they have an `href`.
- HTML with no visible text and no image is stored as null.

## 9. Progress

| Method | Path | Body | Success |
|---|---|---|---|
| PATCH | `/api/v1/items/{itemId}/progress` | `UpdateItemProgressRequest` | `200` `ItemProgressResponse` |
| POST | `/api/v1/topics/{topicId}/complete` | | `200` `TopicCompletionResponse` |
| DELETE | `/api/v1/topics/{topicId}/complete` | | `200` `TopicCompletionResponse` (`completed: false`) |
| POST | `/api/v1/reviews` | `ReviewRequest` | `200` `ReviewResponse` |
| GET | `/api/v1/reviews/due?subjectId=&limit=50` | | `200` `DueItemsResponse` |
| GET | `/api/v1/sessions/resume?subjectId=` | | `200` `ResumeResponse`, or `204` when nothing was opened yet |
| PUT | `/api/v1/sessions` | `SaveSessionRequest` | `200` `ResumeResponse` |

- `UpdateItemProgressRequest`: `status`, `confidence` (1–5; `0` clears it), `bookmarked`. Missing fields stay
  unchanged. The first update creates the progress row.
- `ItemProgressResponse`: `status`, `confidence` (absent when not set), `bookmarked`, `reviewCount`,
  `lastReviewedAt`, `nextReviewAt` (absent until the first review).
- `TopicCompletionResponse`: `topicId`, `completed`, `completedAt`.
- `ReviewRequest`: `itemId`, `result` (`AGAIN`/`HARD`/`GOOD`/`EASY`), both required. `ReviewResponse`:
  `itemId`, `result`, `reviewedAt`, `intervalDays`, `progress` after the review.
- `DueItemsResponse`: `subjectId`, `dueCount` (reviews scheduled before the end of today), `newCount` (items
  never reviewed that are not `DONE`), `items` (`DueItemResponse`, at most `limit` (1–200): overdue first,
  then new items in topic and item order). `DueItemResponse`: `id`, `topicId`, `topicName`, `itemType`,
  `title`, `questionHtml`, `answerHtml`, `notesHtml`, `difficulty`, `status`, `reviewCount`, `nextReviewAt`.
- `SaveSessionRequest`: `subjectId` (required), `topicId`, `itemId`. An item sets the topic to its own; a topic
  of another subject, or an item outside the given topic, is `400`. The UI saves it whenever an item opens.
- `ResumeResponse`: `subjectId`, `subjectName`, `subjectIcon`, `topicId`, `topicName`, `itemId`, `itemTitle`,
  `updatedAt`. Without `subjectId`, the most recently saved subject is returned. A topic or item deleted
  since is left out.

### Review schedule (`progress/ReviewScheduler`)

| Result | Next review | Status |
|---|---|---|
| `AGAIN` | +1 day | `LEARNING` |
| `HARD` | +2 days | `LEARNING` |
| `GOOD` | +3 days × max(1, earlier reviews), at most 365 | `DONE` on the third `GOOD`/`EASY` in a row or when already `DONE`, else `LEARNING` |
| `EASY` | +6 days × max(1, earlier reviews), at most 365 | as `GOOD` |

"Today" is the calendar day in `app.time-zone` (`APP_TIME_ZONE`, default `America/Chicago`; blank = the server's zone), so cards
scheduled for later today are already due.

Tables: `item_progress`, `topic_progress`, `review_log`, `study_sessions`.

## 10. Stats

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/stats/overview` | | `200` `StatsOverviewResponse` |

`StatsOverviewResponse`: `totalItems`, `doneItems`, `learningItems`, `notStartedItems`, `percentDone`,
`totalTopics`, `completedTopics` (marked complete), `dueToday`, `newItems`, `reviewsToday`,
`currentStreakDays` (days in a row with a review, ending today, or yesterday if nothing was reviewed yet
today), `longestStreakDays` (within the last year), `subjects` (per subject: `id`, `name`, `icon`,
`archived`, `topicCount`, `completedTopicCount`, `itemCount`, `doneItemCount`, `learningItemCount`,
`percentDone`, `dueCount`, `newCount`) and `reviewsPerDay` (30 entries of `date`, `reviewCount`, oldest
first, zeros included).

Tables: reads everything above through `SubjectService` and `ProgressService`.
