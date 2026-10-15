# Local / test / evaluation environments

Phase 0.5 separates business data targets while keeping the existing MySQL and Milvus services.

| Use | Profiles | MySQL | Milvus database / collection |
|---|---|---|---|
| Normal owner application | local | localhost:3306/ai_interview | default / knowledge_chunk_vectors_local |
| Ordinary automated tests | test (existing local,test also supported) | localhost:3306/ai_interview_test | default / knowledge_chunk_vectors_test; Milvus disabled |
| Guarded real-service tests | local,test-real | localhost:3306/ai_interview_test | default / knowledge_chunk_vectors_test |
| Real AI baseline | evaluation | localhost:3306/ai_interview_evaluation | default / knowledge_chunk_vectors_evaluation |

`application-evaluation.yaml`, `application-test.yaml`, and `application-test-real.yaml` select dedicated targets. Credentials use `EVALUATION_DB_USERNAME/PASSWORD` or `TEST_DB_USERNAME/PASSWORD`, falling back to the existing `DB_USERNAME/PASSWORD`. Keys and passwords stay outside Git; no new persistent environment variable is required for the default local hosts.

`EnvironmentIsolationInitializer` is registered in main `META-INF/spring.factories`. On the test classpath, a marker also rejects a local-only/unprofiled application context. It validates the resolved JDBC database and Milvus collection, including higher-priority overrides and Hikari JDBC URL/catalog/schema, before infrastructure beans exist. Non-Boot guarded contexts explicitly invoke it after loading ConfigData. It also rejects datasource connection-init SQL that could change targets.

Ordinary tests keep embedding/LLM configuration isolated and Milvus/DeepSeek disabled. Guarded real tests still require both their environment condition and assumption; neither protection was weakened. Do not save `RUN_REAL_*` in IDEA. No real AI test ran in Phase 0.5.

The owner IDEA configuration `AiInterview-Local-Real` now resolves to the canonical local collection. The empty historical smoke collection was retired. Audit documents and cleanup evidence may still mention its old name; those are historical records, not runtime targets.

The two isolated MySQL schemas contain schema-only copies of the actual 13 core tables, with the same FK graph and no copied business rows. `ISOLATED_SCHEMAS.sql` records their creation DDL. Their new auto-increment defaults do not reset the local schema's counters. Neither schema is truncated or replaced by the script.

The cleanup manifest contains exact HIGH-confidence primary keys. Its snapshot stores only primary keys, metadata, and complete-row hashes, never passwords or document/answer bodies. The one-time cleanup executor refuses changed snapshots, changed rows, unlisted child references, or replay. MEDIUM/LOW/UNKNOWN rows were preserved.

Milvus live counts use Strong scalar `count(*)` and complete scalar enumeration. `get_stats.rowCount` can still count deleted segment rows. Preserve both observations; do not compact/rebuild/drop the canonical local collection to make its statistics display zero.

Historical golden/corpus/recovery files refer to the previous synthetic IDs. They are not ready-to-run input for the new empty evaluation schema; prepare and review separate evaluation data only in a later authorized phase. Phase 0.5 did not create formal questions/documents or run a baseline.
