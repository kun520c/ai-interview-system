# Phase 4D4 real ingestion review

Real-service verification passed. Final artifact gate: PASS; temporary runner sources/classes/checkpoints/classpath/attempt marker removed. No P0/P1 runtime or data-integrity finding. The four consolidated Phase4D3 P2 diagnostics remain; there was no source or chunker repair.

The real narrow Spring context used only normal local profile. The project EnvironmentIsolationInitializer remained registered and ran before datasource/Milvus initialization; an additional normal-local target validator rejected any other schema/collection/model. Test/evaluation targets and guarded Smoke-test switches were never enabled or overridden. Automatic SQL initialization was disabled in the temporary context. No full application or HTTP server started.

Production path: KnowledgeDocumentAdminService.uploadDocument → generated UPLOADED document → KnowledgeDocumentAdminService.processDocument → KnowledgeDocumentProcessingService → KnowledgeTextChunker → SpringAiEmbeddingClient / production OpenAiEmbeddingModel → MilvusVectorStoreClient → proxied KnowledgeDocumentProcessingTransactionService → production MyBatis batch insertion and READY. Observing delegates forwarded every authorized operation unchanged and recorded counts/model metadata. They rejected Embedding outside processing, unexpected embedding text, writes to old IDs and all similarity search.

A separate read-only preflight passed first; the ingestion process repeated the full preflight before its first upload. Exact current question/reference/scoring text, type and weight matched the 21/97 frozen bank. A separate read-only JVM then verified every final row/vector with zero Embedding or vector mutations.

The existing production processing implementation reconciles each newly generated document before embedding using deleteByDocumentId and deleteByDocumentIdAndVersion. All 16 calls targeted new IDs 581–596, with no pre-existing chunks/vectors for those IDs. No old document was reprocessed, no raw SQL insertion/manual data repair was used, no retry occurred, and no failure compensation was needed. Source text retained its frozen authoring metadata verbatim.

| Bank | Document ID | READY | Expected/actual chunks | Source/hash | Chunk content | Live vectors |
|---|---:|---|---|---|---|---|
| E01 | 581 | PASS | 2/2 | PASS | PASS | PASS |
| E02 | 582 | PASS | 2/2 | PASS | PASS | PASS |
| E03 | 583 | PASS | 2/2 | PASS | PASS | PASS |
| E04 | 584 | PASS | 2/2 | PASS | PASS | PASS |
| E05 | 585 | PASS | 2/2 | PASS | PASS | PASS |
| M01 | 586 | PASS | 3/3 | PASS | PASS | PASS |
| M03 | 587 | PASS | 2/2 | PASS | PASS | PASS |
| M06 | 588 | PASS | 3/3 | PASS | PASS | PASS |
| M07 | 589 | PASS | 3/3 | PASS | PASS | PASS |
| H01 | 590 | PASS | 3/3 | PASS | PASS | PASS |
| H02 | 591 | PASS | 2/2 | PASS | PASS | PASS |
| H03 | 592 | PASS | 2/2 | PASS | PASS | PASS |
| H04 | 593 | PASS | 2/2 | PASS | PASS | PASS |
| H05 | 594 | PASS | 3/3 | PASS | PASS | PASS |
| H08 | 595 | PASS | 2/2 | PASS | PASS | PASS |
| H09 | 596 | PASS | 3/3 | PASS | PASS | PASS |

All new documents have version=1, MARKDOWN, READY, error_message=NULL and exact title/category/primary official URL/source content. All 38 new chunks have matching parent/version/index/text/UTF-16 length, ACTIVE, unique nonempty vector_id, model qwen3.7-text-embedding, profile qwen3.7-text-embedding-1024-dense-v1, and token_count=NULL. Every vector exists exactly once, matches document/index/version, has 1024 finite values and was verified without similarity search.

Old documents 576–580, chunks 511–524, all original vector IDs and Float32 vector content digests are unchanged. Complete MySQL row digests include timestamps/metadata. Final retrievable MySQL count and live Milvus Strong count are both 52; all six integrity mismatch classes are 0. RAG trace tables remain 0/0. The question bank and other seven business tables have equal ordered before/after row hashes.

Operation counts: 16 uploads; 16 processing operations; 16 real production EmbeddingClient calls with 38 inputs; 16 insert batches with 38 vectors; 16 new-document reconciliation calls; 0 retries/failure compensations. No additional model probe, retrieval, rerank, DeepSeek or Jev invocation.

Verification: offline Maven production compile/classpath BUILD SUCCESS; temporary Java harness compiled; separate preflight, single sequential ingestion and independent verifier exited 0. JUnit/Surefire tests=0, failures=0, errors=0, skipped=0. This is real MySQL/Embedding/Milvus evidence, not a full-regression or Mock result.

Persistent outputs: PHASE_4D4_REAL_INGESTION_MANIFEST.json records actual IDs, hashes, counts and provider observations; PHASE_4D4_CROSS_STORE_INTEGRITY.json records 52 exact identities, old snapshots/digests and zero mismatch counts; this review records scope and findings; ../PHASE_4D4_FULL_BANK_INGESTION_REPORT.md records execution and the final gate. Existing unrelated worktree changes were preserved. These docs/rag artifacts are ignored by the existing Git rules; no ignore rule changed and nothing was staged, committed or pushed.

TEMP_HARNESS_REMOVED: PASS

Stop: PHASE_4D5_STATUS = BLOCKED_PENDING_COMMANDER_REVIEW. Future full-bank retrieval evaluation requires a new design; the old 14-chunk Golden was used only for frozen pilot identity/content verification and was not extended or treated as full-bank relevance.
