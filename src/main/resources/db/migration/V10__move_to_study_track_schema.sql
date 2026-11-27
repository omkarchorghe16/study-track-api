-- Moves every application table from "public" into its own schema, "study_track", gives the vague tables and
-- columns names that say what they hold, and documents every table and column with COMMENT ON (visible in
-- psql "\d+" and in IDE database tools). Flyway's own history table stays in "public".
-- From this migration on, write table names with the schema: study_track.<table>.

CREATE SCHEMA IF NOT EXISTS study_track;

ALTER TABLE users SET SCHEMA study_track;
ALTER TABLE subjects SET SCHEMA study_track;
ALTER TABLE topics SET SCHEMA study_track;
ALTER TABLE study_items SET SCHEMA study_track;
ALTER TABLE tags SET SCHEMA study_track;
ALTER TABLE study_item_tags SET SCHEMA study_track;
ALTER TABLE media_files SET SCHEMA study_track;
ALTER TABLE item_progress SET SCHEMA study_track;
ALTER TABLE topic_progress SET SCHEMA study_track;
ALTER TABLE review_log SET SCHEMA study_track;
ALTER TABLE study_sessions SET SCHEMA study_track;
ALTER TABLE import_jobs SET SCHEMA study_track;
ALTER TABLE import_candidates SET SCHEMA study_track;
ALTER TABLE llm_settings SET SCHEMA study_track;

-- Tables -------------------------------------------------------------------------------------------------
ALTER TABLE study_track.study_items RENAME TO questions;
ALTER TABLE study_track.study_item_tags RENAME TO question_tags;
ALTER TABLE study_track.item_progress RENAME TO question_progress;
ALTER TABLE study_track.review_log RENAME TO flashcard_reviews;
ALTER TABLE study_track.study_sessions RENAME TO resume_points;
ALTER TABLE study_track.import_candidates RENAME TO import_drafts;

-- Columns ------------------------------------------------------------------------------------------------
ALTER TABLE study_track.questions RENAME COLUMN item_type TO question_type;
ALTER TABLE study_track.questions RENAME COLUMN answer_plain TO answer_plain_text;
ALTER TABLE study_track.question_tags RENAME COLUMN item_id TO question_id;
ALTER TABLE study_track.media_files RENAME COLUMN item_id TO question_id;
ALTER TABLE study_track.question_progress RENAME COLUMN item_id TO question_id;
ALTER TABLE study_track.flashcard_reviews RENAME COLUMN item_id TO question_id;
ALTER TABLE study_track.resume_points RENAME COLUMN last_item_id TO last_question_id;
ALTER TABLE study_track.import_jobs RENAME COLUMN media_id TO uploaded_file_id;
ALTER TABLE study_track.import_drafts RENAME COLUMN job_id TO import_job_id;
ALTER TABLE study_track.import_drafts RENAME COLUMN item_type TO question_type;
ALTER TABLE study_track.import_drafts RENAME COLUMN created_item_id TO created_question_id;
ALTER TABLE study_track.import_drafts RENAME COLUMN image_refs TO image_media_ids;

-- Named constraints and indexes follow the new table names ------------------------------------------------
ALTER TABLE study_track.questions RENAME CONSTRAINT ck_study_items_type TO ck_questions_type;
ALTER TABLE study_track.questions RENAME CONSTRAINT ck_study_items_difficulty TO ck_questions_difficulty;
ALTER TABLE study_track.questions RENAME CONSTRAINT ck_study_items_source TO ck_questions_source;
ALTER TABLE study_track.questions RENAME CONSTRAINT fk_study_items_import_job TO fk_questions_import_job;
ALTER TABLE study_track.question_tags RENAME CONSTRAINT pk_study_item_tags TO pk_question_tags;
ALTER TABLE study_track.question_progress RENAME CONSTRAINT ck_item_progress_status TO ck_question_progress_status;
ALTER TABLE study_track.question_progress RENAME CONSTRAINT ck_item_progress_confidence TO ck_question_progress_confidence;
ALTER TABLE study_track.question_progress RENAME CONSTRAINT ck_item_progress_review_count TO ck_question_progress_review_count;
ALTER TABLE study_track.question_progress RENAME CONSTRAINT uq_item_progress_user_item TO uq_question_progress_user_question;
ALTER TABLE study_track.flashcard_reviews RENAME CONSTRAINT ck_review_log_result TO ck_flashcard_reviews_result;
ALTER TABLE study_track.resume_points RENAME CONSTRAINT uq_study_sessions_user_subject TO uq_resume_points_user_subject;
ALTER TABLE study_track.import_drafts RENAME CONSTRAINT ck_import_candidates_type TO ck_import_drafts_type;
ALTER TABLE study_track.import_drafts RENAME CONSTRAINT ck_import_candidates_difficulty TO ck_import_drafts_difficulty;
ALTER TABLE study_track.import_drafts RENAME CONSTRAINT ck_import_candidates_confidence TO ck_import_drafts_confidence;
ALTER TABLE study_track.import_drafts RENAME CONSTRAINT ck_import_candidates_status TO ck_import_drafts_status;

ALTER INDEX study_track.idx_study_items_topic_id RENAME TO idx_questions_topic_id;
ALTER INDEX study_track.idx_study_items_content_hash RENAME TO idx_questions_content_hash;
ALTER INDEX study_track.idx_study_items_search RENAME TO idx_questions_search;
ALTER INDEX study_track.idx_study_item_tags_tag_id RENAME TO idx_question_tags_tag_id;
ALTER INDEX study_track.idx_media_files_item_id RENAME TO idx_media_files_question_id;
ALTER INDEX study_track.idx_item_progress_item_id RENAME TO idx_question_progress_question_id;
ALTER INDEX study_track.idx_item_progress_user_next_review RENAME TO idx_question_progress_user_next_review;
ALTER INDEX study_track.idx_review_log_user_reviewed_at RENAME TO idx_flashcard_reviews_user_reviewed_at;
ALTER INDEX study_track.idx_review_log_item_id RENAME TO idx_flashcard_reviews_question_id;
ALTER INDEX study_track.idx_study_sessions_user_updated_at RENAME TO idx_resume_points_user_updated_at;
ALTER INDEX study_track.idx_import_candidates_job_id RENAME TO idx_import_drafts_import_job_id;

-- What each table and column holds ------------------------------------------------------------------------
COMMENT ON SCHEMA study_track IS 'StudyTrack application data: subjects, topics, questions, study progress and imports.';

COMMENT ON TABLE study_track.users IS 'People who use the app. Until login exists every request acts as user 1 (seeded).';
COMMENT ON COLUMN study_track.users.id IS 'User id; other tables point here with user_id.';
COMMENT ON COLUMN study_track.users.display_name IS 'Name shown in the app.';
COMMENT ON COLUMN study_track.users.email IS 'Unique email address.';
COMMENT ON COLUMN study_track.users.created_at IS 'When the user was created (UTC).';

COMMENT ON TABLE study_track.subjects IS 'A course or prep track a user studies, for example "Core Java". Owns topics.';
COMMENT ON COLUMN study_track.subjects.id IS 'Subject id.';
COMMENT ON COLUMN study_track.subjects.user_id IS 'Owner of the subject (users.id).';
COMMENT ON COLUMN study_track.subjects.name IS 'Subject name, unique per user.';
COMMENT ON COLUMN study_track.subjects.description IS 'Optional short description.';
COMMENT ON COLUMN study_track.subjects.icon IS 'Optional emoji or short icon text shown on the card.';
COMMENT ON COLUMN study_track.subjects.sort_order IS 'Position on the Subjects page (ascending).';
COMMENT ON COLUMN study_track.subjects.archived IS 'True when the subject is hidden from the main list.';
COMMENT ON COLUMN study_track.subjects.created_at IS 'When the subject was created (UTC).';
COMMENT ON COLUMN study_track.subjects.updated_at IS 'When the subject was last changed (UTC).';

COMMENT ON TABLE study_track.topics IS 'A node of a subject''s topic tree (chapters and sub-chapters). Questions belong to a topic.';
COMMENT ON COLUMN study_track.topics.id IS 'Topic id.';
COMMENT ON COLUMN study_track.topics.subject_id IS 'Subject the topic belongs to (subjects.id).';
COMMENT ON COLUMN study_track.topics.parent_id IS 'Parent topic (topics.id); null for a top-level topic.';
COMMENT ON COLUMN study_track.topics.name IS 'Topic name.';
COMMENT ON COLUMN study_track.topics.description_html IS 'Optional description, sanitised HTML.';
COMMENT ON COLUMN study_track.topics.sort_order IS 'Position among its siblings (ascending).';
COMMENT ON COLUMN study_track.topics.created_at IS 'When the topic was created (UTC).';
COMMENT ON COLUMN study_track.topics.updated_at IS 'When the topic was last changed (UTC).';

COMMENT ON TABLE study_track.questions IS 'Study content of a topic: a question with its answer, or a note, example or diagram (question_type).';
COMMENT ON COLUMN study_track.questions.id IS 'Question id.';
COMMENT ON COLUMN study_track.questions.topic_id IS 'Topic the question belongs to (topics.id); the subject comes from the topic.';
COMMENT ON COLUMN study_track.questions.question_type IS 'QUESTION, NOTE, EXAMPLE or DIAGRAM.';
COMMENT ON COLUMN study_track.questions.title IS 'Short title shown in lists; derived from the question when not given.';
COMMENT ON COLUMN study_track.questions.question_html IS 'Question text, sanitised HTML; images as <img src="/api/v1/media/{id}">.';
COMMENT ON COLUMN study_track.questions.answer_html IS 'Answer, sanitised HTML.';
COMMENT ON COLUMN study_track.questions.notes_html IS 'Extra notes, sanitised HTML.';
COMMENT ON COLUMN study_track.questions.answer_plain_text IS 'Answer without HTML tags, for search and flashcards.';
COMMENT ON COLUMN study_track.questions.difficulty IS 'EASY, MEDIUM or HARD.';
COMMENT ON COLUMN study_track.questions.source IS 'MANUAL (typed in the app) or IMPORT (approved from an import).';
COMMENT ON COLUMN study_track.questions.import_job_id IS 'Import that created the question (import_jobs.id); null for MANUAL.';
COMMENT ON COLUMN study_track.questions.content_hash IS 'SHA-256 (hex) of the normalised question text (or the title when there is no question), used to flag duplicates.';
COMMENT ON COLUMN study_track.questions.sort_order IS 'Position within the topic (ascending).';
COMMENT ON COLUMN study_track.questions.search_vector IS 'Generated full-text search vector over title, question, answer and notes.';
COMMENT ON COLUMN study_track.questions.created_at IS 'When the question was created (UTC).';
COMMENT ON COLUMN study_track.questions.updated_at IS 'When the question was last changed (UTC).';

COMMENT ON TABLE study_track.tags IS 'A user''s tag names, for example "jvm"; linked to questions through question_tags.';
COMMENT ON COLUMN study_track.tags.id IS 'Tag id.';
COMMENT ON COLUMN study_track.tags.user_id IS 'Owner of the tag (users.id).';
COMMENT ON COLUMN study_track.tags.name IS 'Lower-case tag name, unique per user.';

COMMENT ON TABLE study_track.question_tags IS 'Which tags a question has (many-to-many between questions and tags).';
COMMENT ON COLUMN study_track.question_tags.question_id IS 'Tagged question (questions.id).';
COMMENT ON COLUMN study_track.question_tags.tag_id IS 'Tag (tags.id).';

COMMENT ON TABLE study_track.media_files IS 'Uploaded files: images used in questions and the documents uploaded for imports. Bytes are on disk under storage_key.';
COMMENT ON COLUMN study_track.media_files.id IS 'File id; HTML references images as /api/v1/media/{id}.';
COMMENT ON COLUMN study_track.media_files.user_id IS 'Owner of the file (users.id).';
COMMENT ON COLUMN study_track.media_files.question_id IS 'Question the image was uploaded for (questions.id); null for documents and unattached images.';
COMMENT ON COLUMN study_track.media_files.original_name IS 'File name as uploaded.';
COMMENT ON COLUMN study_track.media_files.content_type IS 'MIME type, for example image/png or text/csv.';
COMMENT ON COLUMN study_track.media_files.size_bytes IS 'File size in bytes.';
COMMENT ON COLUMN study_track.media_files.storage_key IS 'Path of the file inside the media storage folder.';
COMMENT ON COLUMN study_track.media_files.width IS 'Image width in pixels; null for documents.';
COMMENT ON COLUMN study_track.media_files.height IS 'Image height in pixels; null for documents.';
COMMENT ON COLUMN study_track.media_files.alt_text IS 'Alternative text of an image.';
COMMENT ON COLUMN study_track.media_files.created_at IS 'When the file was uploaded (UTC).';

COMMENT ON TABLE study_track.question_progress IS 'A user''s study state for one question. No row means NOT_STARTED.';
COMMENT ON COLUMN study_track.question_progress.id IS 'Row id.';
COMMENT ON COLUMN study_track.question_progress.user_id IS 'Student (users.id).';
COMMENT ON COLUMN study_track.question_progress.question_id IS 'Question (questions.id).';
COMMENT ON COLUMN study_track.question_progress.status IS 'NOT_STARTED, LEARNING or DONE.';
COMMENT ON COLUMN study_track.question_progress.confidence IS 'Self-rated confidence 1-5; null when not rated.';
COMMENT ON COLUMN study_track.question_progress.review_count IS 'How many times the question was reviewed as a flashcard.';
COMMENT ON COLUMN study_track.question_progress.last_reviewed_at IS 'Last flashcard review (UTC).';
COMMENT ON COLUMN study_track.question_progress.next_review_at IS 'When the flashcard is due again (spaced repetition, UTC).';
COMMENT ON COLUMN study_track.question_progress.bookmarked IS 'True when the student starred the question.';
COMMENT ON COLUMN study_track.question_progress.updated_at IS 'When the row last changed (UTC).';

COMMENT ON TABLE study_track.topic_progress IS 'Topics a user marked complete.';
COMMENT ON COLUMN study_track.topic_progress.id IS 'Row id.';
COMMENT ON COLUMN study_track.topic_progress.user_id IS 'Student (users.id).';
COMMENT ON COLUMN study_track.topic_progress.topic_id IS 'Topic (topics.id).';
COMMENT ON COLUMN study_track.topic_progress.completed IS 'True while the topic is marked complete.';
COMMENT ON COLUMN study_track.topic_progress.completed_at IS 'When it was marked complete (UTC).';

COMMENT ON TABLE study_track.flashcard_reviews IS 'History of flashcard answers: one row per review, used for streaks and the activity chart.';
COMMENT ON COLUMN study_track.flashcard_reviews.id IS 'Row id.';
COMMENT ON COLUMN study_track.flashcard_reviews.user_id IS 'Student (users.id).';
COMMENT ON COLUMN study_track.flashcard_reviews.question_id IS 'Reviewed question (questions.id).';
COMMENT ON COLUMN study_track.flashcard_reviews.result IS 'How the student rated their recall (AGAIN, HARD, GOOD or EASY).';
COMMENT ON COLUMN study_track.flashcard_reviews.reviewed_at IS 'When the review happened (UTC).';

COMMENT ON TABLE study_track.resume_points IS 'Where a user left off in each subject, for "Continue where you left off".';
COMMENT ON COLUMN study_track.resume_points.id IS 'Row id.';
COMMENT ON COLUMN study_track.resume_points.user_id IS 'Student (users.id).';
COMMENT ON COLUMN study_track.resume_points.subject_id IS 'Subject (subjects.id); one row per user and subject.';
COMMENT ON COLUMN study_track.resume_points.last_topic_id IS 'Last topic opened (topics.id).';
COMMENT ON COLUMN study_track.resume_points.last_question_id IS 'Last question opened (questions.id).';
COMMENT ON COLUMN study_track.resume_points.updated_at IS 'When the resume point was saved (UTC).';

COMMENT ON TABLE study_track.import_jobs IS 'One uploaded file (Word, PDF, Markdown, text or CSV) being turned into questions for a subject.';
COMMENT ON COLUMN study_track.import_jobs.id IS 'Import id.';
COMMENT ON COLUMN study_track.import_jobs.user_id IS 'User who uploaded the file (users.id).';
COMMENT ON COLUMN study_track.import_jobs.subject_id IS 'Subject the questions are imported into (subjects.id).';
COMMENT ON COLUMN study_track.import_jobs.file_name IS 'Uploaded file name.';
COMMENT ON COLUMN study_track.import_jobs.file_type IS 'DOCX, PDF, MD, TXT or CSV.';
COMMENT ON COLUMN study_track.import_jobs.uploaded_file_id IS 'The stored upload (media_files.id); null once the file is deleted.';
COMMENT ON COLUMN study_track.import_jobs.status IS 'UPLOADED, PARSING, EXTRACTING, READY_FOR_REVIEW, COMPLETED or FAILED.';
COMMENT ON COLUMN study_track.import_jobs.llm_provider IS 'LLM that read the document (ANTHROPIC, OPENAI, GROQ, OLLAMA).';
COMMENT ON COLUMN study_track.import_jobs.llm_model IS 'LLM model id used.';
COMMENT ON COLUMN study_track.import_jobs.total_chunks IS 'Parts the document was split into (one LLM call each).';
COMMENT ON COLUMN study_track.import_jobs.processed_chunks IS 'Parts processed so far.';
COMMENT ON COLUMN study_track.import_jobs.failed_chunks IS 'Parts the LLM could not process.';
COMMENT ON COLUMN study_track.import_jobs.input_tokens IS 'LLM input tokens used by the whole import.';
COMMENT ON COLUMN study_track.import_jobs.output_tokens IS 'LLM output tokens used by the whole import.';
COMMENT ON COLUMN study_track.import_jobs.error_message IS 'Why the import failed, shown to the user.';
COMMENT ON COLUMN study_track.import_jobs.created_at IS 'When the file was uploaded (UTC).';
COMMENT ON COLUMN study_track.import_jobs.updated_at IS 'When the import last changed (UTC).';

COMMENT ON TABLE study_track.import_drafts IS 'Questions found in an import, waiting for the user to approve (copied to questions) or reject.';
COMMENT ON COLUMN study_track.import_drafts.id IS 'Draft id.';
COMMENT ON COLUMN study_track.import_drafts.import_job_id IS 'Import that found it (import_jobs.id).';
COMMENT ON COLUMN study_track.import_drafts.chunk_index IS 'Part of the document it came from (0-based).';
COMMENT ON COLUMN study_track.import_drafts.suggested_topic_path IS 'Topic path to file it under, for example "Java > Collections"; when empty, a topic named after the uploaded file.';
COMMENT ON COLUMN study_track.import_drafts.question_type IS 'QUESTION, NOTE, EXAMPLE or DIAGRAM.';
COMMENT ON COLUMN study_track.import_drafts.title IS 'Short title.';
COMMENT ON COLUMN study_track.import_drafts.question_html IS 'Question text, sanitised HTML.';
COMMENT ON COLUMN study_track.import_drafts.answer_html IS 'Answer, sanitised HTML.';
COMMENT ON COLUMN study_track.import_drafts.notes_html IS 'Extra notes, sanitised HTML.';
COMMENT ON COLUMN study_track.import_drafts.difficulty IS 'EASY, MEDIUM or HARD.';
COMMENT ON COLUMN study_track.import_drafts.tags IS 'Tag names, comma-separated.';
COMMENT ON COLUMN study_track.import_drafts.image_media_ids IS 'Ids of images (media_files.id) used in the draft, comma-separated.';
COMMENT ON COLUMN study_track.import_drafts.confidence_score IS 'How sure the LLM was, 0 to 1; null for CSV rows.';
COMMENT ON COLUMN study_track.import_drafts.content_hash IS 'Same hash as questions.content_hash, to flag duplicates.';
COMMENT ON COLUMN study_track.import_drafts.status IS 'PENDING, EDITED, APPROVED or REJECTED.';
COMMENT ON COLUMN study_track.import_drafts.target_topic_id IS 'Topic chosen by the user (topics.id); overrides suggested_topic_path.';
COMMENT ON COLUMN study_track.import_drafts.created_question_id IS 'Question created when approved (questions.id).';
COMMENT ON COLUMN study_track.import_drafts.sort_order IS 'Order in the review list.';

COMMENT ON TABLE study_track.llm_settings IS 'A user''s choice of LLM for imports. API keys are never stored here; they come from the server environment.';
COMMENT ON COLUMN study_track.llm_settings.id IS 'Row id.';
COMMENT ON COLUMN study_track.llm_settings.user_id IS 'User (users.id); one row per user.';
COMMENT ON COLUMN study_track.llm_settings.provider IS 'ANTHROPIC, OPENAI, GROQ or OLLAMA.';
COMMENT ON COLUMN study_track.llm_settings.model IS 'Model id, for example claude-opus-5-5 or openai/gpt-oss-120b.';
COMMENT ON COLUMN study_track.llm_settings.base_url IS 'Provider URL; null means the provider default.';
COMMENT ON COLUMN study_track.llm_settings.enabled IS 'False switches imports off.';
COMMENT ON COLUMN study_track.llm_settings.updated_at IS 'When the settings were saved (UTC).';
