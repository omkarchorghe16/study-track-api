-- Questions store their subject and owner directly, so a user's questions per subject are one indexed query
-- (no join through topics). Both are copied from the question's topic and kept in step by the API whenever a
-- question is created or moved to another topic.
ALTER TABLE study_track.questions ADD COLUMN subject_id BIGINT;
ALTER TABLE study_track.questions ADD COLUMN user_id BIGINT;

UPDATE study_track.questions q
SET subject_id = t.subject_id, user_id = s.user_id
FROM study_track.topics t JOIN study_track.subjects s ON s.id = t.subject_id
WHERE t.id = q.topic_id;

ALTER TABLE study_track.questions ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE study_track.questions ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE study_track.questions ADD CONSTRAINT fk_questions_subject
    FOREIGN KEY (subject_id) REFERENCES study_track.subjects (id) ON DELETE CASCADE;
ALTER TABLE study_track.questions ADD CONSTRAINT fk_questions_user
    FOREIGN KEY (user_id) REFERENCES study_track.users (id);

CREATE INDEX idx_questions_subject_id ON study_track.questions (subject_id, sort_order);
CREATE INDEX idx_questions_user_id ON study_track.questions (user_id);

COMMENT ON COLUMN study_track.questions.subject_id IS 'Subject of the question (subjects.id); always the subject of its topic.';
COMMENT ON COLUMN study_track.questions.user_id IS 'Student who owns the question (users.id); always the owner of its subject.';
