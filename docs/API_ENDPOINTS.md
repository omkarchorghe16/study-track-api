# StudyTrack API — Endpoint Reference

Base URL (local): `http://localhost:8083` · All paths are prefixed with `/api/v1`.
OpenAPI JSON: `/v3/api-docs` · Swagger UI: `/swagger-ui.html` · Health: `/actuator/health`.

This document describes, for every endpoint, its request/response contract, error cases and which
database tables it reads or writes. Endpoints from later phases are listed in `docs/BUILD_PLAN.md`
section 4 and move here as they are built.

---

## 1. Database overview

Schema is managed by Flyway (`src/main/resources/db/migration`, V1–V16). Since V10 every table lives in the
PostgreSQL schema **`study_track`** (Flyway's `flyway_schema_history` stays in `public`), and every table and
column has a `COMMENT` (see `\d+ study_track.questions` in psql or the IDE's database view). Hibernate runs
with `ddl-auto: validate` and `hibernate.default_schema: study_track`; native SQL writes `study_track.<table>`.
Java class names are older than the V10 table names; the Entity column maps them.

| Table (`study_track.`) | Purpose | Key columns | Entity |
|---|---|---|---|
| `users` | People who own StudyTrack data | `id` identity, `display_name` ≤100, `email` ≤255 UNIQUE, `created_at`, `is_admin` (V14; may change the catalog). V1 seeds user `1` (`Default user`, an admin), the user every request acts as until login exists | `UserEntity` |
| `subjects` | A user's courses or prep tracks | `id`, `user_id` → `users`, `catalog_subject_id` → `catalog_subjects` ON DELETE SET NULL (V14; the catalog subject it was copied from, or null), `name` ≤120 UNIQUE per user, `description` ≤1000, `icon` ≤16, `sort_order`, `archived`, `created_at`, `updated_at` | `SubjectEntity` |
| `topics` | Topic tree inside a subject | `id`, `subject_id` → `subjects` ON DELETE CASCADE, `parent_id` → `topics` ON DELETE CASCADE (null = top level, never itself), `name` ≤200, `description_html`, `sort_order`, timestamps | `TopicEntity` |
| `questions` | Questions with answers, and notes, examples, diagrams | `id`, `topic_id` → `topics` ON DELETE CASCADE, `subject_id` → `subjects` ON DELETE CASCADE and `user_id` → `users` (V12; always the topic's subject and its owner, set on create and move), `question_type` (`QUESTION`/`NOTE`/`EXAMPLE`/`DIAGRAM`), `title` ≤300, `question_html`, `answer_html`, `notes_html`, `answer_plain_text` (answer without tags), `difficulty` (`EASY`/`MEDIUM`/`HARD`, default `MEDIUM`), `source` (`MANUAL`/`IMPORT`/`CATALOG`), `import_job_id` → `import_jobs` ON DELETE SET NULL, `content_hash` (SHA-256 of the normalised question text, or title), `sort_order`, `search_vector` (PostgreSQL only, generated), timestamps | `StudyItemEntity` |
| `tags` | A user's tags | `id`, `user_id` → `users`, `name` ≤50 lower-case, UNIQUE(`user_id`,`name`) | `TagEntity` |
| `question_tags` | Question ↔ tag links | PK(`question_id`,`tag_id`), both ON DELETE CASCADE | `StudyItemEntity.tags` |
| `question_progress` | A user's progress on a question (no row = `NOT_STARTED`) | `id`, `user_id`, `question_id` → `questions` ON DELETE CASCADE, `status` (`NOT_STARTED`/`LEARNING`/`DONE`), `confidence` 1–5 or null, `review_count`, `last_reviewed_at`, `next_review_at`, `bookmarked`, `updated_at`; UNIQUE(`user_id`,`question_id`) | `ItemProgressEntity` |
| `topic_progress` | Manual "mark complete" | `id`, `user_id`, `topic_id` → `topics` ON DELETE CASCADE, `completed`, `completed_at`; UNIQUE(`user_id`,`topic_id`) | `TopicProgressEntity` |
| `catalog_tracks` | Shared study tracks, e.g. "Full-Stack Java Developer" (V14) | `id`, `name` ≤120 UNIQUE, `description`, `sort_order`, `created_at` | `CatalogTrackEntity` |
| `catalog_subjects` | Shared subjects of a track | `id`, `track_id` → `catalog_tracks` ON DELETE CASCADE, `name` ≤120 UNIQUE per track, `description`, `icon`, `sort_order` | `CatalogSubjectEntity` |
| `catalog_topics` | Shared topic tree of a catalog subject | `id`, `catalog_subject_id` → `catalog_subjects` ON DELETE CASCADE, `parent_id` → `catalog_topics` ON DELETE CASCADE, `name` ≤200, `sort_order` | `CatalogTopicEntity` |
| `catalog_questions` | Starter questions and answers under a catalog topic (V15), plain text | `id`, `catalog_topic_id` → `catalog_topics` ON DELETE CASCADE, `question` ≤1000, `answer`, `difficulty` (`EASY`/`MEDIUM`/`HARD`), `sort_order` | `CatalogQuestionEntity` |
| `question_explanations` | AI-written detailed explanation of a question (V16), next to its own answer | `id`, `question_id` → `questions` ON DELETE CASCADE UNIQUE, `user_id`, `content` (JSON text), `llm_provider`, `llm_model`, `created_at`, `updated_at` | `QuestionExplanationEntity` |
| `flashcard_reviews` | One row per flashcard rating | `id`, `user_id`, `question_id` → `questions` ON DELETE CASCADE, `result` (`AGAIN`/`HARD`/`GOOD`/`EASY`), `reviewed_at` | `ReviewLogEntity` |
| `resume_points` | "Continue where you left off", per subject | `id`, `user_id`, `subject_id` → `subjects` ON DELETE CASCADE, `last_topic_id` → `topics` ON DELETE SET NULL, `last_question_id` → `questions` ON DELETE SET NULL, `updated_at`; UNIQUE(`user_id`,`subject_id`) | `StudySessionEntity` |
| `media_files` | Uploaded images and import documents | `id`, `user_id` → `users`, `question_id` → `questions` ON DELETE SET NULL (null = not used by a question yet, or a document), `original_name` ≤255, `content_type`, `size_bytes` >0, `storage_key` UNIQUE (`yyyy/MM/<uuid>.<ext>` under `app.media.storage-dir`), `width`, `height`, `alt_text` ≤500, `created_at` | `MediaFileEntity` |
| `import_jobs` | One uploaded file being imported | `id`, `user_id`, `subject_id` → `subjects` ON DELETE CASCADE, `file_name` ≤255, `file_type` (`DOCX`/`PDF`/`MD`/`TXT`/`CSV`), `uploaded_file_id` → `media_files` ON DELETE SET NULL (the stored original), `status` (`UPLOADED`/`PARSING`/`EXTRACTING`/`READY_FOR_REVIEW`/`COMPLETED`/`FAILED`), `llm_provider`, `llm_model` (both null for CSV), `total_chunks`, `processed_chunks`, `failed_chunks`, `input_tokens`, `output_tokens`, `error_message` ≤1000, timestamps | `ImportJobEntity` |
| `import_drafts` | Questions found by an import, saved to `questions` at once (V13: `DUPLICATE` when the subject already had them) | `id`, `import_job_id` → `import_jobs` ON DELETE CASCADE, `chunk_index`, `suggested_topic_path` ≤500 (`A > B`), `question_type`, `title` ≤300, `question_html`, `answer_html`, `notes_html` (sanitised), `difficulty`, `tags` (comma list), `image_media_ids` (comma list of `media_files` ids), `confidence_score` 0–1 (null for CSV), `content_hash`, `status` (`APPROVED` saved, `DUPLICATE` skipped; `PENDING`/`EDITED` not saved yet, older imports; `REJECTED`), `target_topic_id` → `topics` ON DELETE SET NULL, `created_question_id` → `questions` ON DELETE SET NULL, `sort_order` | `ImportCandidateEntity` |
| `llm_settings` | A user's LLM for imports (no row = `app.llm` defaults) | `id`, `user_id` UNIQUE, `provider` (`ANTHROPIC`/`OPENAI`/`GROQ`/`OLLAMA`), `model` ≤100, `base_url` ≤500 (null = provider default), `enabled`, `updated_at`. **No API key column**: keys come only from `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GROQ_API_KEY` | `LlmSettingsEntity` |

V10 renamed `study_items` → `questions`, `study_item_tags` → `question_tags`, `item_progress` →
`question_progress`, `review_log` → `flashcard_reviews`, `study_sessions` → `resume_points`, `import_candidates`
→ `import_drafts`, and the columns `item_id` → `question_id`, `item_type` → `question_type`, `answer_plain` →
`answer_plain_text`, `last_item_id` → `last_question_id`, `media_id` → `uploaded_file_id`, `job_id` →
`import_job_id`, `created_item_id` → `created_question_id`, `image_refs` → `image_media_ids`.

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
| 409 | Duplicate subject name for the user (case-insensitive); an import that cannot start (imports switched off, LLM not configured) or a review action while the import still runs or on a candidate already approved/rejected |
| 413 | Upload larger than `app.media.max-image-bytes` (5 MB), `app.import.max-file-bytes` (20 MB) or the multipart limit (20 MB) |
| 415 | Image upload that is not a PNG, JPEG, GIF or WebP image (checked by content, not by name), import that is not a .docx, .pdf, .md, .txt or .csv file whose content matches, or an unsupported request content type |
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
Tables: `subjects`; counts read `topics`, `questions`, `question_progress` and `topic_progress` with three grouped queries.

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

Tables: `topics`; the tree is built from one query, one grouped count over `questions` with
`question_progress`, and one read of `topic_progress`.

## 7. Items

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/topics/{topicId}/items?includeSubtopics=false&status=&difficulty=&tag=&page=0&size=20` | | `200` `ItemPageResponse` |
| GET | `/api/v1/subjects/{subjectId}/items?status=&difficulty=&tag=&page=0&size=20` | | `200` `ItemPageResponse`: the current user's questions of the subject (`questions.subject_id`, `user_id`), ordered by topic then item (each has `topicId`); `404` for another user's subject |
| GET | `/api/v1/items/{itemId}` | | `200` `ItemResponse` |
| POST | `/api/v1/items` | `ItemRequest` | `201` + `Location`, `ItemResponse` |
| POST | `/api/v1/items/duplicate-check` | `DuplicateCheckRequest` | `200` list of `DuplicateItemResponse` (empty when none) |
| PUT | `/api/v1/items/{itemId}` | `ItemRequest` | `200` `ItemResponse` |
| DELETE | `/api/v1/items/{itemId}` | | `204` |

- List: the topic's own items by `sortOrder` then `id`; with `includeSubtopics=true` (default `false`) also the items of all its subtopics at every depth, ordered by topic (`sortOrder`, `id`), then item. The Angular app always sends `true`, so clicking a topic or a subtopic lists everything under it. `difficulty` is `EASY`/`MEDIUM`/`HARD`
  (other values `400`), `status` is `NOT_STARTED`/`LEARNING`/`DONE` for the current user, `tag` matches
  case-insensitively, `page` ≥0, `size` 1–100.
- `ItemRequest`: `topicId` (required; a different topic on PUT moves the item), `itemType` (required),
  `title` (required, ≤300), `questionHtml`, `answerHtml`, `notesHtml` (≤500,000 chars each), `difficulty`
  (missing = `MEDIUM`), `tags` (≤20 names, ≤50 chars; trimmed, lower-cased, de-duplicated, created when new),
  `sortOrder` (missing = last on create or when moving, otherwise unchanged).
- `ItemResponse`: all fields above plus `id`, `subjectId`, `source`, sorted `tags`, `previousItemId` and
  `nextItemId` (neighbours in the topic, or absent), `progress` (`ItemProgressResponse`, see section 9),
  `createdAt`, `updatedAt`.
- Duplicate check (nothing is saved): `DuplicateCheckRequest` is `topicId` and `title` (required),
  `questionHtml`, and `itemId` (the item being edited, never reported). It returns up to 5 items of the
  topic's subject with the same `content_hash` (same question text, or title when there is no question,
  ignoring case, spacing and formatting), oldest first: `id`, `title`, `topicId`, `topicName`.
- `ItemPageResponse`: `items` (`id`, `topicId`, `itemType`, `title`, `difficulty`, `tags`, `sortOrder`,
  `hasAnswer`, `status`, `bookmarked`, `updatedAt`), `page`, `size`, `totalItems`, `totalPages`.

On every save the service sets `answer_plain` (answer text without tags, via jsoup) and `content_hash`.
Question, answer and notes HTML are sanitised with jsoup on every save (see section 8, Rich content), so
the response can differ from what was sent. Images the item references are linked to it in `media_files`;
images an edit no longer references are unlinked (kept, `item_id` cleared) so they can be used again, and
all of the item's linked images are deleted with their files when the item is deleted.

### Search

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/items/search?q=&subjectId=&page=0&size=20` | | `200` `SearchPageResponse` |

- `q` (required, ≤200 chars): its words (letters and digits; quotes, operators and punctuation are
  dropped, at most 10) must all match, each as a word prefix (`hash` finds "HashMap"). A query with no
  words returns no results. `subjectId` limits to one subject (`404` if not the user's), `page` 0–1000,
  `size` 1–50.
- PostgreSQL: `questions.search_vector` (V8, generated from title A, question B, answer C, notes D,
  English stemming, GIN index), `to_tsquery('english', 'word:* & …')`, ordered by `ts_rank`, then newest.
  Other databases (H2 in tests) fall back to case-insensitive `LIKE` on the same fields, title matches first.
- `SearchPageResponse`: `query` (trimmed), `items`, `page`, `size`, `totalItems`, `totalPages`. Each item:
  `itemId`, `title`, `itemType`, `difficulty`, `status` (current user), `subjectId`, `subjectName`,
  `topicId`, `topicName`, `snippet`.
- `snippet` is plain text from the first of question, answer and notes that contains a match, cut to about
  220 characters around it, as parts `{text, match}`; `match` marks words to highlight. It is never HTML.

Tables: `questions`, `topics`, `subjects`, `question_progress` (native SQL in `search/ItemSearchRepository`).

Tables: `questions`, `tags`, `question_tags`, `question_progress`; tags and progress for a page are loaded in one
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

**Unused image cleanup** (`MediaCleanupJob`, `app.media.cleanup-cron`, default daily 03:30 server time;
`-` turns it off): deletes rows and files of images older than `app.media.cleanup-grace` (`MEDIA_CLEANUP_GRACE`,
default 7 days) that nothing uses: no linked item, not an import's stored document, and no
`src="/api/v1/media/{id}"` in any item, topic description or open (PENDING/EDITED) import candidate. The HTML
check keeps images that several items share. It logs only the count.

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

Tables: `question_progress`, `topic_progress`, `flashcard_reviews`, `resume_points`.

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

## 11. Imports (LLM, or CSV without an LLM)

| Method | Path | Body | Success |
|---|---|---|---|
| POST | `/api/v1/imports` | `multipart/form-data`: `file` (.docx, .pdf, .md/.markdown, .txt, .csv, ≤20 MB), `subjectId` | `202` + `Location`, `ImportJobResponse` |
| GET | `/api/v1/imports?limit=20` | | `200` list of `ImportJobResponse`, newest first (limit 1–100) |
| GET | `/api/v1/imports/{importId}` | | `200` `ImportJobResponse` (poll every 2–3 s while running) |
| GET | `/api/v1/imports/{importId}/candidates` | | `200` list of `CandidateResponse` in document order (the questions found, with `questionStatus` of each saved one) |
| PUT | `/api/v1/imports/{importId}/candidates/{candidateId}` | `CandidateRequest` | `200` `CandidateResponse` (status `EDITED`); only for drafts not saved yet |
| POST | `/api/v1/imports/{importId}/approve` | `CandidateSelectionRequest` | `200` `ApproveResponse`; saves drafts an older import left unsaved (new imports save everything themselves) |
| POST | `/api/v1/imports/{importId}/reject` | `CandidateSelectionRequest` | `200` `RejectResponse`; drops unsaved drafts |

**CSV** (`importer/CsvQuestionReader`): column 1 the question, column 2 the answer, optional column 3 the topic
path (`Java > Collections`). RFC 4180 quoting (commas, line breaks and `""` inside quotes), UTF-8 (a BOM is
skipped), comma delimiter or semicolon/tab when the first line has no comma. A first row starting with
`question`,`answer` is a header and skipped, as are blank lines and rows with no question; more than 5,000
questions or an unclosed quote fails the job with the line number. No LLM is called, so a CSV works with LLM
imports switched off or no API key; the job has no `llmProvider`/`llmModel` and zero tokens. Each row becomes a
draft (cells are plain text, HTML is escaped; difficulty `MEDIUM`, no confidence) and is saved as a question at
once, like every import (below). Rows without a topic go to a topic named after the file.

**No review step.** When an import ends (`COMPLETED`, or `FAILED` part-way, which keeps what was found), the
processing thread publishes `ImportDraftsReadyEvent` and `ImportService.saveImportDrafts` saves every open draft
as a question through the approve logic. A draft whose question the subject already has, or that repeats an
earlier draft, is marked `DUPLICATE` and not saved again. The import page then lists the questions with a
"completed" checkbox: ticking it is `PATCH /api/v1/items/{id}/progress` `{"status":"DONE"}` (unticking sends
`NOT_STARTED`), stored in `question_progress` for the current user (1), and returned as `questionStatus`.

**Pipeline** (`importer/ImportProcessingService`, `@Async` on the `importTaskExecutor`, `app.import.worker-threads`):

1. Upload: the type comes from the extension and must match the bytes (ZIP for DOCX, `%PDF` for PDF,
   UTF-8 for MD/TXT). The original is stored as a `media_files` row (served by `GET /media/{id}`). The
   LLM settings are checked first: imports switched off, or a provider whose API key is not set, is `409`
   with the reason. The job records the provider and model; processing starts after the row commits.
2. Parse (`PARSING`, `DocumentParser`): DOCX through Mammoth to HTML, every embedded image stored as a
   `media_files` row (PNG/JPEG/GIF/WebP up to `app.media.max-image-bytes`; others are left out) and
   replaced by `[[IMG:{mediaId}]]`. PDF through PDFBox, one `[Page n]` text block per page. Markdown and
   text are read as they are, split at blank lines (Markdown `#`–`###` headings start sections; fenced code stays whole).
3. Chunk (`DocumentChunker`): headings h1–h3 start sections; sections are packed into chunks of at most
   `app.llm.max-chunk-tokens` (6,000, estimated at 4 characters per token) and the heading path where each
   chunk starts (`Spring Boot > Security`) goes with it. Over `app.llm.max-chunks-per-job` (200) chunks fails the job.
4. Extract (`EXTRACTING`): one LLM call per chunk with the system prompt and JSON Schema in
   `importer/ExtractionPrompt` (keep the author's wording and HTML formatting, keep placeholders, never
   invent answers, document text is data not instructions). An answer that is not valid JSON gets one
   retry with a correction; a rate limit, 5xx or timeout gets one retry after `app.llm.retry-delay`.
   A chunk the model refuses, truncates or answers with invalid JSON twice is counted in `failedChunks`
   and skipped; a configuration or authentication failure fails the job with the provider's short message.
5. Save (`CandidateContentBuilder`): placeholders become `<img src="/api/v1/media/{id}">` (only the job's
   own images), plain text becomes paragraphs, HTML goes through `HtmlSanitizer`, tags are lower-cased
   (≤10), the title comes from the question (or notes/answer), and `content_hash` is computed like an
   item's. Drafts and job counters are saved per chunk; at the end the drafts are saved as questions and the
   job is `COMPLETED` (or `FAILED` when every chunk failed). Token usage is logged per job; document text, prompts, answers
   and keys are never logged.
6. A restart marks jobs that were still running as `FAILED` ("The server restarted …").

- `ImportJobResponse`: `id`, `subjectId`, `fileName`, `fileType`, `status`, `llmProvider`, `llmModel`,
  `totalChunks`, `processedChunks`, `failedChunks`, `percentComplete` (100 once finished), `inputTokens`,
  `outputTokens`, `errorMessage` (when `FAILED`), `candidates` (`total`, `open` = not saved yet, `approved` =
  saved as questions, `rejected`, `duplicates` = skipped because the subject had them), `createdAt`, `updatedAt`.
- `CandidateResponse`: `id`, `chunkIndex`, `status`, `itemType`, `title`, `questionHtml`, `answerHtml`,
  `notesHtml`, `difficulty`, `tags`, `imageIds`, `confidence` (the LLM's 0–1 estimate), `suggestedTopicPath`,
  `targetTopicId`, `createdItemId` (the saved question), `questionStatus` (the user's status of that question;
  `DONE` = ticked completed), and for open and `DUPLICATE` drafts `duplicateItemId` (the question of the subject
  with the same normalised question) or `duplicateOfCandidateId` (an earlier draft of the same import).
- `CandidateRequest`: `itemType` and `title` (≤300) required; `questionHtml`, `answerHtml`, `notesHtml`
  (sanitised), `difficulty`, `tags` (≤20, each ≤50), `topicPath` (≤500, `A > B`), `targetTopicId` (a topic of
  the import's subject; wins over `topicPath`). Only `PENDING`/`EDITED` candidates can be edited (`409` otherwise).
- `CandidateSelectionRequest`: `candidateIds` (≤1000) or `"all": true` (every open candidate). Ids of
  another import are `404`; an empty list without `all` is `400`; approved or rejected candidates are skipped.
- Approve, in document order: the target topic, else the topic path walked from the top level (matching
  names case-insensitively, creating missing topics), else a topic named after the file (`notes.docx` →
  `notes`). Items are created with `source = IMPORT` and `import_job_id`, and their images are linked; duplicates
  become `DUPLICATE`. `ApproveResponse`: `approvedCount` (questions created), `createdTopicCount`, `createdItemIds`, `job`.
  `RejectResponse`: `rejectedCount`, `job`. The job becomes `COMPLETED` when no candidate is open.
- Approve, reject and edit while the job is `UPLOADED`/`PARSING`/`EXTRACTING` are `409`.

Tables: `import_jobs`, `import_drafts`, `media_files`; saving writes `topics`, `questions`, `tags`,
`question_tags`.

## 12. LLM settings

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/settings/llm` | | `200` `LlmSettingsResponse` |
| PUT | `/api/v1/settings/llm` | `LlmSettingsRequest` | `200` `LlmSettingsResponse` |
| POST | `/api/v1/settings/llm/test` | `LlmSettingsRequest` (optional) | `200` `LlmConnectionTestResponse` |

- `LlmSettingsRequest`: `provider` (`ANTHROPIC`/`OPENAI`/`GROQ`/`OLLAMA`) and `model` (≤100) required, `baseUrl`
  (≤500; blank = provider default; must be `http(s)://host…` without user info, query or fragment, trailing
  `/` removed), `enabled` (default true; false makes new imports `409`). **API keys are never accepted or
  returned**; they come from `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` / `GROQ_API_KEY` on the server.
- `LlmSettingsResponse`: `provider`, `model`, `baseUrl`, `enabled`, `saved` (false while the `app.llm`
  defaults apply), `providers` (each `provider`, `defaultModel`, `defaultBaseUrl`, `apiKeyRequired`,
  `apiKeyConfigured`).
- Test checks the body's (unsaved) settings, or the saved ones without a body, without generating text:
  Anthropic `GET /v1/models/{model}`, OpenAI `GET /v1/models/{model}`, Groq `GET /v1/models` (the model must be listed), Ollama `GET /api/tags` (the model must
  be pulled). `LlmConnectionTestResponse`: `ok`, `message` (the reason when not ok), `provider`, `model`.

**Providers** (`llm` package, Spring `RestClient`, no vendor SDKs; `LlmClientFactory` picks the client):

| Provider | Call | Notes |
|---|---|---|
| Anthropic (default) | `POST /v1/messages`, `anthropic-version: 2026-10-21` | Default model `claude-opus-5-5`; structured output (`output_config.format` = the JSON Schema), `output_config.effort` from `ANTHROPIC_EFFORT` (default `medium`). On models that support it, the server-side refusal fallback is on (`anthropic-beta: server-side-fallback-2026-07-01`, `fallbacks: "default"`). |
| OpenAI | `POST /v1/chat/completions` | `response_format` strict `json_schema`; default model `gpt-4o-mini` |
| Groq | `POST /openai/v1/chat/completions` (OpenAI-compatible) | Same request as OpenAI; default model `openai/gpt-oss-120b`. Free-tier sized: chunks of `GROQ_MAX_CHUNK_TOKENS` (1,500) and `max_completion_tokens` `GROQ_MAX_OUTPUT_TOKENS` (4,000). A `429` waits for `Retry-After` (≤60 s) and is sent again up to 3 times. |
| Ollama | `POST /api/chat` (`stream: false`) | Local and free; the schema goes in `format`, `num_ctx` from `OLLAMA_CONTEXT_TOKENS` |

Connect timeout 10 s, read timeout `app.llm.timeout` (`LLM_TIMEOUT`, default 5 minutes), `max_tokens`
`app.llm.max-output-tokens` (16,000). Provider errors become short messages (`Anthropic rejected the API
key (HTTP 401): …`); response bodies are never logged.

Tables: `llm_settings`.

## 13. Catalog (shared tracks, subjects and topics)

A shared, read-only-for-users library of what to study: tracks (for example *Full-Stack Java Developer*, *DevOps Engineer*, *Data Engineer*, *QA and Test Automation Engineer*,
*Python Developer*, *Angular Developer*, *React Developer*) hold subjects (*Core Java*), which hold a topic tree
(*Collections › HashMap*). It holds topics and starter questions with answers. An empty catalog is filled at startup from
`src/main/resources/catalog/*.csv` (`app.catalog.seed-on-startup`, env `CATALOG_SEED_ON_STARTUP`); it runs once and
never when a track already exists, so files added later are loaded by uploading them through the admin import (it only adds what is missing, so uploading all of them again is safe). Tables: `catalog_tracks`, `catalog_subjects`, `catalog_topics`, `catalog_questions` and
`subjects.catalog_subject_id`. The seed files are `01`–`07` (topics) and `11`–`17` `*-questions.csv` (starter questions); the AI-drafted answers should be reviewed before relying on them.

| Method and path | What it does |
|---|---|
| `GET /api/v1/users/me` | `{id, displayName, admin}`; the UI shows the import-catalog button only to admins |
| `GET /api/v1/catalog/tracks` | Tracks with `subjectCount`, by sort order |
| `GET /api/v1/catalog/tracks/{trackId}/subjects` | Subjects with `topicCount`, `questionCount` and `copiedSubjectId` (id of the user's own subject when already added, else absent). `404` for an unknown track |
| `GET /api/v1/catalog/subjects/{catalogSubjectId}/topics` | Topic tree `[{id, name, children}]`. `404` for an unknown subject |
| `POST /api/v1/catalog/subjects/{catalogSubjectId}/enroll` | Creates the user's own subject (name, description, icon) with a copy of the whole topic tree and its starter questions (questions with `source` `CATALOG`, formatted as HTML); `201` with the subject (as in section 5). The copy is independent of later catalog changes. `409` when already added or when the user has a subject with that name; `404` for an unknown subject |
| `GET /api/v1/catalog/template.csv` | A sample catalog CSV (`catalog-template.csv`) |
| `POST /api/v1/admin/catalog/import` | Admin only (`403` otherwise). Multipart field `file`: CSV with columns `track`, `subject`, optional `topic_path` (levels separated by `>`) and optional `question`, `answer`, `difficulty` (a starter question under that topic: needs a `topic_path`; answers use blank lines for paragraphs, `- ` lists, `` `code` `` and ``` fences; repeated questions, ignoring case and spacing, are skipped); a header row, blank lines, a UTF-8 BOM, quoted cells and `,` `;` or tab delimiters are accepted; at most 20,000 rows. Adds what is missing, matching names ignoring case; never changes or deletes existing rows or users' subjects. Response `{rowsRead, tracksAdded, subjectsAdded, topicsAdded, questionsAdded, rejectedRowCount, rejectedRows}`: rows without a track or subject, a question without a topic, a bad difficulty, or too-long text, are listed (first 20, `Line n: reason`) and skipped. `400` for an unreadable CSV (for example an unclosed quote) |

Until login exists the current user is user 1, who is an admin (`users.is_admin`).

## 14. Explanations (AI, detailed answers)

An AI-written, structured explanation of a question, kept **next to** the question's own answer (the answer is never
changed). It uses the provider and model saved on the Settings page (Groq, OpenAI, Anthropic or Ollama; the API key
only ever comes from the server's environment). The model returns JSON that the app draws as cards: `summary`,
`analogy`, `keyPoints`, `howItWorks` (steps), `example` (title, language, code, explanation), `realWorldExample`
(title, scenario, howItApplies), `commonMistakes`, `interviewTip`. It is plain text, never HTML, so nothing needs
sanitising; the app escapes it. Text is trimmed, lists are capped at 8 items and code at 4,000 characters; an
unusable reply is retried once with a correction. Table: `question_explanations`.

| Method and path | What it does |
|---|---|
| `GET /api/v1/items/{questionId}/explanation` | `200` `{questionId, content, provider, model, updatedAt}`, or `404` when there is none yet |
| `POST /api/v1/items/{questionId}/explanation` | Writes (or rewrites) it with the AI, replacing an earlier one; can take up to a minute. `409` when AI is turned off in Settings or the provider's API key is not set, `424` when the provider failed or gave no usable answer (the message is safe to show), `404` for an unknown question |
| `POST /api/v1/subjects/{subjectId}/explanations` | `202`: explains every QUESTION of the subject that has none yet, in the background, one after another (rate limits are waited out by the Groq client). Reports `{state, total, completed, failed, missing, message}`; starting again while running just reports |
| `GET /api/v1/subjects/{subjectId}/explanations/status` | The same report: `state` is `IDLE`, `RUNNING`, `COMPLETED` or `FAILED` (stopped because AI is off or the key is missing); `missing` counts questions still without one. Progress is in memory (lost on restart); saved explanations are not |

Prompts and answers are never logged.
