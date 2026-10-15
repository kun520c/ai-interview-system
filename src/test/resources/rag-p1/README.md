# Public offline RAG fixture

`public-replay.json` contains eight MAIN/FOLLOW_UP cases and forty authored synthetic candidates. Question and scoring-point text is reused from project-owned interview inputs; vector/document/chunk IDs, evidence content, COSINE scores and advisory labels are synthetic. This is a business-contract regression fixture, not retrieval-quality evidence or a saved paid provider response.

The test uses production QueryBuilder, retrieval, Prompt, Trace, validation and scoring objects with mocked external boundaries. It covers threshold boundaries, raw-hit retention, SQL hydration order, zero-evidence behavior, MAIN/FOLLOW_UP context and label separation.

From the repository root:

```powershell
.\mvnw.cmd -B -ntp "-Dtest=RagP1OfflineBusinessReplayTest,RagP1IndependentQuerySetTest,KnowledgeRetrievalThresholdTest" test
```

These tests need Java 21 and Maven dependencies, but no database, Milvus, API keys or local experiment files. `local-replay.json` is private historical evidence, remains ignored and is not loaded by published tests. Test output goes to `target/test-replays/`; it does not overwrite historical experiment reports.
