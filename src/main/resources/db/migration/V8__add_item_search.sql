-- Full-text search on items (GET /api/v1/items/search). search_vector is computed by PostgreSQL from the
-- title (weight A), the question text (B), the answer text (C) and the notes text (D); HTML tags are
-- stripped with a regular expression. The GIN index makes "search_vector @@ query" fast.
-- Hibernate does not map this column; ItemSearchRepository queries it with native SQL.
ALTER TABLE study_items ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(regexp_replace(question_html, '<[^>]*>', ' ', 'g'), '')), 'B') ||
    setweight(to_tsvector('english', coalesce(answer_plain, '')), 'C') ||
    setweight(to_tsvector('english', coalesce(regexp_replace(notes_html, '<[^>]*>', ' ', 'g'), '')), 'D')
) STORED;

CREATE INDEX idx_study_items_search ON study_items USING GIN (search_vector);
