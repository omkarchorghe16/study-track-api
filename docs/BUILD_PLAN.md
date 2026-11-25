# StudyTrack – Build Plan for Claude Code

> **Status (2026-11-25):** Phases 1 to 6 are built. Phase 7 (accounts and deployment) is deferred and not to be
> started until it is asked for. Copied from the original Word plan; the copy here is the one to keep up to date.

A study tracker where anyone (students, engineers, interview candidates) keeps their notes as subjects, topics and Q&A items with rich content and images, marks topics complete, resumes where they left off, and imports Word/PDF notes that an LLM turns into questions and answers.

This plan follows the same conventions as Omkar's Folio Pro repositories (folio-portfolio-api, folio-market-feed-svc, folio-pro-ui). Give it to Claude Code one phase at a time.

## 0. How to use this plan with Claude Code

1.  Create two empty folders side by side with the Folio repos and dev-standards: study-track-api and study-track-ui.

2.  Open Claude Code in the parent folder and paste this message:

> Read StudyTrack-Plan.md. Use ../folio-portfolio-api and ../folio-pro-ui as the reference for structure, coding style, CLAUDE.md, .github/instructions, Flyway, logging, tests, Docker and k8s. Use the shared rules from ../dev-standards (run its sync.sh for the new repos). Implement **Phase 1 only**, run the checks listed for that phase, then stop and summarise what you built and what I should test.

3.  Test it yourself, commit, then ask for the next phase. Small phases keep the output reviewable.

## 1. Tech stack (same as Folio Pro)

**Backend – study-track-api**

- Java 21, Maven, Spring Boot 3.5.x

- spring-boot-starter-web, validation, data-jpa, actuator, aop (LoggingAspect like Folio)

- PostgreSQL + Flyway (Flyway owns the schema, Hibernate only validates)

- springdoc-openapi for Swagger UI

- H2 + test-schema.sql for tests, same as Folio

- Document parsing: org.zwobble.mammoth:mammoth (DOCX → clean HTML with images), Apache PDFBox (PDF text), org.jsoup:jsoup (HTML sanitising and splitting)

- LLM calls with Spring RestClient behind an interface (no vendor SDK lock-in)

- Port **8083**, database study_track on the same local PostgreSQL

- Dockerfile, compose.yaml, .env.example, k8s/ folder copied from the Folio pattern

**Frontend – study-track-ui**

- Angular 22, standalone components, ShellComponent layout like Folio Pro

- Port **4201**, proxy.conf.json routes /api to 8083

- Rich text editor: ngx-quill (stores HTML)

- Safe rendering: DOMPurify + Angular DomSanitizer

- Code highlighting: highlight.js; optional diagrams: mermaid

- Charts on the dashboard: chart.js via ng2-charts

## 2. Architecture

Angular UI (4201) → /api/v1/\* → Spring Boot API (8083) → PostgreSQL study_track

Inside the API, the Folio layering: controller → service → repository/model, DTOs in dto, errors in exception/ApiExceptionHandler, logging in config/LoggingAspect.

Package by feature under com.studytrack.api:

- subject – subjects (a course or prep track, e.g. "Java Interview")

- topic – topic tree inside a subject

- item – questions/answers/notes

- media – images and attachments

- progress – status, topic completion, resume point, review log

- importer – file upload, parsing, LLM extraction, review queue

- llm – provider interface and implementations

- stats – dashboard numbers

User handling: start like Folio with a fixed userId = 1 from config, but put user_id on every user-owned table from the first migration so login (Phase 7) needs no schema rewrite.

## 3. Database design (Flyway migrations)

**V1\_\_create_users.sql**

- users: id, display_name, email (unique), created_at. Seed user 1.

**V2\_\_create_subjects_topics.sql**

- subjects: id, user_id, name, description, icon, sort_order, archived, created_at, updated_at

- topics: id, subject_id, parent_id (nullable, self-reference for subtopics), name, description_html, sort_order, created_at, updated_at

**V3\_\_create_items.sql**

- study_items: id, topic_id, item_type (QUESTION, NOTE, EXAMPLE, DIAGRAM), title, question_html, answer_html, notes_html, answer_plain (tags stripped, for search and AI), difficulty (EASY, MEDIUM, HARD), source (MANUAL, IMPORT), import_job_id (nullable), content_hash (to skip duplicates), sort_order, created_at, updated_at

- tags: id, user_id, name (unique per user)

- study_item_tags: item_id, tag_id (composite key)

**V4\_\_create_media.sql**

- media_files: id, user_id, item_id (nullable until linked), original_name, content_type, size_bytes, storage_key (path on disk now, S3 key later), width, height, alt_text, created_at

- HTML stores images as \<img src="/api/v1/media/{id}"\>, never as base64.

**V5\_\_create_progress.sql**

- item_progress: id, user_id, item_id, status (NOT_STARTED, LEARNING, DONE), confidence (1–5), review_count, last_reviewed_at, next_review_at, bookmarked; unique (user_id, item_id)

- topic_progress: id, user_id, topic_id, completed (manual "mark complete"), completed_at; unique (user_id, topic_id)

- review_log: id, user_id, item_id, result (AGAIN, HARD, GOOD, EASY), reviewed_at

- study_sessions: id, user_id, subject_id, last_topic_id, last_item_id, updated_at – this is the "resume where I left off" pointer

**V6\_\_create_import.sql**

- import_jobs: id, user_id, subject_id, file_name, file_type (DOCX, PDF, MD, TXT), media_id (stored original), status (UPLOADED, PARSING, EXTRACTING, READY_FOR_REVIEW, COMPLETED, FAILED), llm_provider, llm_model, total_chunks, processed_chunks, error_message, created_at, updated_at

- import_candidates: id, job_id, chunk_index, suggested_topic_path (e.g. "Spring Boot \> Security"), item_type, question_html, answer_html, notes_html, difficulty, tags (comma list), image_refs, confidence_score, status (PENDING, APPROVED, REJECTED, EDITED), target_topic_id, created_item_id

**V7\_\_create_llm_settings.sql**

- llm_settings: id, user_id, provider (ANTHROPIC, OPENAI, OLLAMA), model, base_url, enabled. API keys stay in environment variables, never in the database.

Relations: subject 1–n topics; topic self-references for subtopics; topic 1–n items; item n–n tags; item 1–n media; user+item → 1 item_progress; user+topic → 1 topic_progress; import_job 1–n candidates; approved candidate → 1 item.

Mirror every migration in src/test/resources/test-schema.sql, as the Folio rules require.

## 4. REST API (/api/v1)

**Subjects and topics**

- GET /subjects – list with progress % per subject

- POST /subjects, PUT /subjects/{id}, DELETE /subjects/{id}

- GET /subjects/{id}/topics – full topic tree with counts and progress

- POST /topics, PUT /topics/{id}, DELETE /topics/{id}, PATCH /topics/{id}/move (new parent/sort order)

**Items**

- GET /topics/{id}/items?status=&difficulty=&tag=&page=&size=

- GET /items/{id}, POST /items, PUT /items/{id}, DELETE /items/{id}

- GET /items/search?q= – Postgres full-text search on title and answer_plain

**Media**

- POST /media (multipart) → returns id and URL; the editor uses this for pasted/uploaded images

- GET /media/{id} – streams the file with correct content type and cache headers

- DELETE /media/{id}

**Progress**

- PATCH /items/{id}/progress – status, confidence, bookmarked

- POST /topics/{id}/complete and DELETE /topics/{id}/complete – mark/unmark topic complete

- POST /reviews – log a flashcard result; server computes next_review_at

- GET /reviews/due?subjectId= – items due today

- GET /sessions/resume?subjectId= and PUT /sessions – read/save where the user left off

**Import (LLM)**

- POST /imports (multipart file + subjectId) → creates job, returns 202 with job id

- GET /imports/{id} – status and progress (UI polls every 2–3 s)

- GET /imports/{id}/candidates

- PUT /imports/{id}/candidates/{cid} – edit a candidate or change its target topic

- POST /imports/{id}/approve – body: candidate ids (or all) → creates topics if missing, creates items, links media

- POST /imports/{id}/reject – candidate ids

**Stats and settings**

- GET /stats/overview – totals, % done per subject and topic, streak, reviews per day (last 30 days)

- GET /settings/llm, PUT /settings/llm, POST /settings/llm/test – checks the connection

Document every endpoint in docs/API_ENDPOINTS.md and add a Postman collection, as in Folio.

## 5. LLM import pipeline

1.  **Upload and store**: save the original file through the media service; create import_jobs row (UPLOADED). Limit 20 MB; accept .docx, .pdf, .md, .txt.

2.  **Parse** (async with @Async + a dedicated executor):

    - DOCX: Mammoth → HTML. Save every embedded image as a media_files row and replace it in the HTML with a placeholder \[\[IMG:{mediaId}\]\].

    - PDF: PDFBox → text per page (images optional later).

    - MD/TXT: read as is.

3.  **Chunk**: split the HTML with jsoup at headings (h1–h3) and keep chunks under ~6,000 tokens. Keep the heading path with each chunk so the LLM knows the topic context.

4.  **Extract**: for each chunk call LlmClient.extract(chunk) with a system prompt that asks for JSON only, matching this schema: { "items": \[ { "topicPath": "Spring Boot \> Security", "type": "QUESTION", "question": "...", "answerHtml": "...", "notesHtml": "...", "difficulty": "MEDIUM", "tags": \["jwt"\], "imageRefs": \[12\], "confidence": 0.9 } \] } Rules in the prompt: keep the author's wording and formatting (bold, lists, code, tables) as HTML; keep \[\[IMG:n\]\] placeholders where they belong; never invent answers; if a question has no answer, return an empty answer.

5.  **Validate**: parse JSON with Jackson; on invalid JSON retry once with a "fix your JSON" message; sanitise HTML with jsoup Safelist.relaxed() plus pre, code, table and img with src limited to /api/v1/media/. Replace placeholders with \<img src="/api/v1/media/{id}"\>.

6.  **Save candidates** (status PENDING), update job counters, finish with READY_FOR_REVIEW.

7.  **Review in the UI**: the user edits, re-assigns topics, approves or rejects. Nothing goes into study_items without approval.

8.  **Duplicates**: compute content_hash from normalised question text; flag candidates that match an existing item.

**Provider abstraction (llm package)**

- interface LlmClient { LlmExtractionResult extract(LlmExtractionRequest request); boolean testConnection(); }

- AnthropicLlmClient (Claude Messages API), OpenAiLlmClient, OllamaLlmClient (local, free, good for testing)

- LlmClientFactory picks the implementation from llm_settings / application.yml

- Config in LlmProperties (@ConfigurationProperties("app.llm")): provider, model, base-url, api-key from env (ANTHROPIC_API_KEY, OPENAI_API_KEY), timeout, max-chunk-tokens, max-chunks-per-job

- Log token usage per job, never log the key or the full document text

- Tests use a FakeLlmClient returning fixed JSON; no real API calls in mvn test

## 6. Angular UI

Routes (lazy-loaded features under src/app/features/):

- /dashboard – overall progress ring, per-subject progress bars, "Continue where you left off" card, items due today, 30-day activity chart

- /subjects – subject cards (name, icon, % complete), create/edit dialog

- /subjects/:id – two-pane layout: topic tree on the left (with check marks for completed topics and % per topic), item list on the right with filters (status, difficulty, tag) and a "Mark topic complete" button

- /items/:id – reading view: question, rendered answer HTML with images, code highlighting, notes, status buttons (Learning / Done), confidence stars, bookmark, previous/next item

- /items/:id/edit and /items/new?topicId= – Quill editor for question, answer, notes; image upload goes to POST /media

- /study/:subjectId – flashcard mode for due items: show question, reveal answer, rate Again/Hard/Good/Easy

- /import – wizard: 1) choose subject and upload file, 2) progress bar while the job runs, 3) review table of candidates with inline preview and edit, topic picker, approve/reject in bulk

- /search – global search results

- /settings – LLM provider, model, test connection; theme

Shared pieces:

- core/services/\* – one HTTP service per backend area (subjects, topics, items, media, progress, imports, stats, settings), with models in core/models

- shared/components/rich-content – renders stored HTML safely (DOMPurify → bypassSecurityTrustHtml) and runs highlight.js on pre code

- shared/components/progress-bar, topic-tree, confirm-dialog

- Save the resume point (PUT /sessions) whenever the user opens an item

- Follow the Folio UI accessibility instructions from dev-standards

## 7. Spaced repetition rule (simple, can improve later)

- AGAIN → next review tomorrow, status LEARNING

- HARD → +2 days

- GOOD → +3 days × max(1, review_count)

- EASY → +6 days × max(1, review_count)

- Three GOOD/EASY results in a row → status DONE

- A topic shows as complete when the user marks it, or (as a hint) when every item in it is DONE

Put this in progress/ReviewScheduler with unit tests so it can be replaced by SM-2 later.

## 8. Phases for Claude Code

Each phase ends with: mvn test green, npx ng build with no errors or budget warnings, the strict tsc check, README/docs updated.

**Phase 1 – Scaffolding** Both repos created with Folio structure, CLAUDE.md, .github/instructions (project rules for StudyTrack), dev-standards synced, Dockerfile, compose, .env.example, health endpoint, Swagger, Angular shell with empty routes and proxy. Database study_track created; V1 applied.

**Phase 2 – Subjects, topics, items (CRUD)** V2–V3, entities, repositories, services, controllers, DTOs, integration tests. UI: subjects page, topic tree, item list, item reading view (plain HTML render), item editor with Quill.

**Phase 3 – Media and rich content** V4, upload/serve/delete, size and type validation, jsoup sanitising on every save. UI: image upload in editor, rich-content component with DOMPurify and highlight.js.

**Phase 4 – Progress tracking** V5, item status, topic complete, resume pointer, review log, scheduler, stats endpoint. UI: dashboard, progress bars, mark complete, continue card, flashcard study mode.

**Phase 5 – LLM import** V6–V7, async pipeline, Mammoth/PDFBox parsing, chunking, LlmClient with Anthropic + Ollama first, candidate review endpoints, FakeLlmClient tests. UI: import wizard, review table, settings page.

**Phase 6 – Search and polish** Postgres full-text index on items, search page, duplicate detection, keyboard shortcuts in study mode, empty states, error toasts.

**Phase 7 – Accounts and deployment** Spring Security with JWT (register/login), replace fixed user id with the authenticated user, per-user data checks in every service, Angular auth guard and interceptor. Docker images, k8s manifests, media storage switch to S3 via a MediaStorage interface.

## 9. Later ideas (design already allows them)

- AI tutor: "quiz me on this topic" – LLM asks a question, grades the typed answer against answer_plain

- Generate missing answers or examples for a question (always saved as a candidate for review)

- Share a subject publicly or with a study group (add subject_members table)

- Mock interview sessions with score history

- Export a subject to PDF/Markdown

- Mobile-friendly PWA

## 10. Acceptance checklist for the first usable version (after Phase 5)

- Import my Java interview Word file and see questions grouped by topic with formatting and diagrams intact

- Edit any answer with bold, lists, code and images

- Mark items Learning/Done and topics complete

- Dashboard shows % complete and a working "Continue" button

- Flashcard mode shows only items due today

- Switching the LLM provider in settings works without code changes
