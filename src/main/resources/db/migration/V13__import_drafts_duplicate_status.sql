-- Imports now save every question they find straight away. A question the subject already has is not saved
-- again; its draft gets the new status DUPLICATE.
ALTER TABLE study_track.import_drafts DROP CONSTRAINT ck_import_drafts_status;
ALTER TABLE study_track.import_drafts ADD CONSTRAINT ck_import_drafts_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EDITED', 'DUPLICATE'));

COMMENT ON TABLE study_track.import_drafts IS 'Questions found by an import. They are saved to questions at once (APPROVED, created_question_id set); a question the subject already had is DUPLICATE and not saved again.';
COMMENT ON COLUMN study_track.import_drafts.status IS 'APPROVED (saved as a question), DUPLICATE (the subject already had it), REJECTED, or PENDING/EDITED (not saved yet; older imports only).';
