-- Run once against each intended schema, before deploying the new application.
-- Stop/drain old application instances first: old code does not honor fencing.
-- Nullable columns preserve all historical rows. Claim/retry/reclaim sets a fresh ID.
ALTER TABLE interview_answer ADD COLUMN attempt_id varchar(32) NULL AFTER request_id;
ALTER TABLE knowledge_document ADD COLUMN processing_attempt_id varchar(32) NULL AFTER processing_status;
