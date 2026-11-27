-- CSV imports: rows are read directly (column 1 question, column 2 answer, optional column 3 topic path),
-- so a CSV import has no LLM provider or model.
ALTER TABLE study_track.import_jobs DROP CONSTRAINT ck_import_jobs_file_type;
ALTER TABLE study_track.import_jobs ADD CONSTRAINT ck_import_jobs_file_type
    CHECK (file_type IN ('DOCX', 'PDF', 'MD', 'TXT', 'CSV'));

ALTER TABLE study_track.import_jobs ALTER COLUMN llm_provider DROP NOT NULL;
ALTER TABLE study_track.import_jobs ALTER COLUMN llm_model DROP NOT NULL;

COMMENT ON COLUMN study_track.import_jobs.llm_provider IS 'LLM that read the document (ANTHROPIC, OPENAI, GROQ, OLLAMA); null for CSV imports.';
COMMENT ON COLUMN study_track.import_jobs.llm_model IS 'LLM model id used; null for CSV imports.';
