-- PREPARED ONLY. Future one-time administrator action, after explicit authorization.
-- Use a private, non-repository working copy and replace the two password markers there.
-- Never execute with mysql --force. Existing accounts stop CREATE USER; do not change their grants blindly.
-- No schema/data import and no global/source write capability is granted.
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: replace passwords privately and remove this guard only after authorization';

CREATE USER 'rag_e2e_source_ro'@'127.0.0.1' IDENTIFIED BY '__PRIVATE_SOURCE_PASSWORD__';
GRANT SELECT ON `ai_interview`.`question` TO 'rag_e2e_source_ro'@'127.0.0.1';
GRANT SELECT ON `ai_interview`.`question_scoring_point` TO 'rag_e2e_source_ro'@'127.0.0.1';
GRANT SELECT ON `ai_interview`.`knowledge_document` TO 'rag_e2e_source_ro'@'127.0.0.1';
GRANT SELECT ON `ai_interview`.`knowledge_chunk` TO 'rag_e2e_source_ro'@'127.0.0.1';

CREATE USER 'rag_e2e_writer'@'127.0.0.1' IDENTIFIED BY '__PRIVATE_TEST_PASSWORD__';
GRANT SELECT, INSERT ON `ai_interview_test`.`user` TO 'rag_e2e_writer'@'127.0.0.1';
-- Required by the existing SELECT user ... FOR UPDATE concurrency boundary. No user UPDATE is executed.
GRANT UPDATE (`id`) ON `ai_interview_test`.`user` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT ON `ai_interview_test`.`question` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT ON `ai_interview_test`.`question_scoring_point` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT ON `ai_interview_test`.`knowledge_document` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT ON `ai_interview_test`.`knowledge_chunk` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `ai_interview_test`.`interview_session` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `ai_interview_test`.`interview_question` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE ON `ai_interview_test`.`interview_answer` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT ON `ai_interview_test`.`answer_evaluation` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT ON `ai_interview_test`.`rag_retrieval_batch` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT, INSERT ON `ai_interview_test`.`rag_hit_log` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT ON `ai_interview_test`.`interview_report` TO 'rag_e2e_writer'@'127.0.0.1';
GRANT SELECT ON `ai_interview_test`.`user_weakness` TO 'rag_e2e_writer'@'127.0.0.1';

-- No FLUSH PRIVILEGES is necessary for CREATE USER/GRANT.
-- Existing r08_source_ro may instead be reused: its complete self-grants must pass the runner's read-only audit.
