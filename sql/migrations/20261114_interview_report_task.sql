-- M1-B: execution requires explicit approval for each exact instance/schema.
-- Applied only to the approved 127.0.0.1:3308/ai_interview_test on 2026-11-14.
-- Manual additive migration, once per explicitly approved schema. No USE / CREATE DATABASE.
-- sql/ai_interview.sql remains the pre-M1-B bootstrap snapshot; apply this file after it
-- only for a separately approved NEW schema, or once to an approved existing schema.
-- No automatic migration, IF NOT EXISTS, DROP, or data repair. Check table absence first.
-- Stop/drain old synchronous report writers and reconcile historical GENERATING first.
-- DDL implicitly commits; rollback retains this table and its historical rows.
CREATE TABLE interview_report_task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  attempt_version INT UNSIGNED NOT NULL,
  execution_version INT UNSIGNED NULL,
  task_state VARCHAR(16) NOT NULL,
  delivery_state VARCHAR(16) NOT NULL,
  dispatch_token CHAR(36) NULL,
  dispatch_lease_until DATETIME(6) NULL,
  next_send_at DATETIME(6) NOT NULL,
  send_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  queued_at DATETIME(6) NOT NULL,
  queue_deadline DATETIME(6) NOT NULL,
  started_at DATETIME(6) NULL,
  execution_deadline DATETIME(6) NULL,
  failure_code VARCHAR(40) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_task_session_attempt (session_id, attempt_version),
  KEY idx_report_task_pending (task_state, delivery_state, next_send_at, id),
  KEY idx_report_task_lease (task_state, delivery_state, dispatch_lease_until, id),
  KEY idx_report_task_queue_expiry (task_state, queue_deadline, id),
  KEY idx_report_task_execution_expiry (task_state, execution_deadline, id),
  CONSTRAINT fk_report_task_session FOREIGN KEY (session_id) REFERENCES interview_session(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_report_task_state CHECK (task_state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
  CONSTRAINT chk_report_task_delivery CHECK (delivery_state IN ('PENDING','LEASED','SENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
