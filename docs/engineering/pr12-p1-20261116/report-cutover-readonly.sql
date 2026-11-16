-- MySQL 8.0; run only on an explicitly approved instance/schema with all three tables present.
-- If interview_report_task is absent, use the pre-migration queries in README.md instead.
-- No credentials, schema selection, DDL, row locks, business writes or task creation.
-- Snapshot evidence only: repeat after old writers have stopped, before enabling new acceptance.
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;
SELECT DATABASE() AS target_schema, @@hostname AS server_hostname, VERSION() AS mysql_version,
       CURRENT_USER() AS account, UTC_TIMESTAMP(6) AS checked_at_utc;

WITH task_counts AS (
    SELECT session_id, COUNT(*) AS tasks,
           SUM(task_state IN ('QUEUED','RUNNING')) AS active_tasks,
           SUM(task_state IN ('SUCCEEDED','FAILED')) AS terminal_tasks,
           SUM(task_state = 'FAILED') AS failed_tasks
    FROM interview_report_task GROUP BY session_id
)
SELECT COUNT(*) AS generating_sessions,
       COALESCE(SUM(r.id IS NOT NULL),0) AS generating_with_report,
       COALESCE(SUM(COALESCE(t.active_tasks,0)=0),0) AS generating_without_active_task,
       COALESCE(SUM(r.id IS NULL AND COALESCE(t.active_tasks,0)=0),0) AS no_report_no_active_task,
       COALESCE(SUM(t.terminal_tasks>0 AND t.active_tasks=0),0) AS terminal_tasks_only,
       COALESCE(SUM(t.failed_tasks>0 AND t.active_tasks=0),0) AS failed_tasks_without_active,
       COALESCE(SUM(t.active_tasks>1),0) AS multiple_active_tasks,
       COALESCE(SUM(s.status<>'COMPLETED'),0) AS generating_not_completed
FROM interview_session s
LEFT JOIN task_counts t ON t.session_id=s.id
LEFT JOIN interview_report r ON r.session_id=s.id
WHERE s.report_status='GENERATING';

SELECT COUNT(*) AS active_tasks,
       COALESCE(SUM(s.report_status<>'GENERATING' OR s.status<>'COMPLETED'),0) AS active_with_invalid_session_state,
       COALESCE(SUM(NOT (s.version <=> CASE WHEN t.task_state='RUNNING'
                       THEN t.execution_version ELSE t.attempt_version END)),0) AS ownership_version_mismatches,
       COALESCE(SUM((t.task_state='QUEUED' AND t.execution_version IS NOT NULL)
                    OR (t.task_state='RUNNING' AND NOT (t.execution_version <=> t.attempt_version+1))),0)
                    AS invalid_execution_versions,
       COALESCE(SUM(CASE WHEN t.task_state='RUNNING' THEN t.execution_deadline
                        ELSE t.queue_deadline END <= UTC_TIMESTAMP(6)),0) AS expired_active_tasks
FROM interview_report_task t JOIN interview_session s ON s.id=t.session_id
WHERE t.task_state IN ('QUEUED','RUNNING');

SELECT COALESCE(SUM(s.report_status='READY' AND r.id IS NULL),0) AS ready_without_report,
       COALESCE(SUM(s.report_status<>'READY' AND r.id IS NOT NULL),0) AS report_without_ready
FROM interview_session s LEFT JOIN interview_report r ON r.session_id=s.id;

-- IDs/state/version only. Does not export user identities, answers, report text or raw model output.
SELECT s.id AS session_id, s.status, s.report_status, s.version AS session_version,
       r.id AS report_id, t.id AS task_id, t.task_state, t.delivery_state,
       t.attempt_version, t.execution_version, t.failure_code,
       CASE WHEN t.task_state='RUNNING' THEN t.execution_version
            WHEN t.task_state='QUEUED' THEN t.attempt_version END AS expected_session_version
FROM interview_session s
LEFT JOIN interview_report r ON r.session_id=s.id
LEFT JOIN interview_report_task t ON t.session_id=s.id
WHERE s.report_status='GENERATING' OR (s.report_status='READY' AND r.id IS NULL)
   OR (s.report_status<>'READY' AND r.id IS NOT NULL)
   OR t.task_state IN ('QUEUED','RUNNING')
ORDER BY s.id,t.id;

SELECT task_state, delivery_state, COUNT(*) AS task_count
FROM interview_report_task GROUP BY task_state,delivery_state;
COMMIT;
