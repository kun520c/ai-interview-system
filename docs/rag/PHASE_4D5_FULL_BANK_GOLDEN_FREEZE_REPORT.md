# Phase 4D5 full-bank Golden freeze report

Frozen before any Embedding/similarity/retrieval output. Execution date: 2026-10-06, Asia/Shanghai. The semantic review uses the exact 21 frozen production queries, their 97 database scoring-point IDs/texts and the 52 previously produced chunks. No chunker rerun. The old 294 judgment objects are unchanged, including old query/other-topic judgments despite new first-class documents.

| Check | Actual |
|---|---|
| Queries | 21 |
| Chunks | 52 = old 14 + new 38 |
| Judgments | 1092 = old 294 + new 798 |
| Queries with DIRECT | 21/21 |
| Uncertain judgments | 0 |
| New DIRECT / RELATED_ONLY / IRRELEVANT | 47 / 28 / 723 |

Read-only live preflight passed: question=21, scoring_point=97, documents=21, chunks=52, Milvus Strong entities=52, traces=0/0; document row hashes, all exact chunk texts/eligibility, 52 vector IDs/metadata/Float32 digests match Phase4D4. The existing production schema validator verified 1024/COSINE/AUTOINDEX without its creation branch. Current MAIN EvaluationContext/query-builder reconstruction matched all 21 frozen query hashes. No source/code/config/old artifact drift in the 359-file inventory.

Judgments were finalized without Phase4B/Phase4C3 scores/ranks or current Milvus similarity results. New own-topic passages were reviewed, including useful body-tail choices and reference-only exclusions. Cross-topic decisions are explained in PHASE_4D5_FULL_BANK_GOLDEN_REVIEW.md; they are explicit semantics, not a category/keyword classifier. Every DIRECT has verbatim evidence and valid exact query scoring-point IDs. A compound point may receive evidence for a stated specific mechanism/component, as in the preserved old Golden; that does not assert full question or point coverage.

| Frozen artifact | SHA-256 |
|---|---|
| docs/rag/evaluation/PHASE_4A_PRODUCTION_QUERY_SET.json | 8251e2ee7307b9a03ec258328e7f29d91475d51b640f11a37c4f04d29e029d35 |
| docs/rag/evaluation/PHASE_4A_GOLDEN_RELEVANCE.json | f3870b3432856dd44efbe384656367b798bb730d4c9313bdc6cee6259ac1cd22 |
| docs/rag/full-bank/PHASE_4D3_PRODUCTION_CHUNK_MANIFEST.json | ce60ac53d7075924d7c24ed7ffc3fab2c33bd363a7521c59f20c37e353dfb34c |
| docs/rag/full-bank/PHASE_4D3_SCORING_POINT_CHUNK_COVERAGE.json | 16c67abc7fa02d324ca81cf4f7dd7409be4e3161a1bcd273e7d7059d33e63a04 |
| docs/rag/full-bank/PHASE_4D2_AUTHORING_EVIDENCE_MATRIX.json | 022423b8ee1d118a00427611d6fe07a7f2c050d62e55b6582e34b3ff47889c34 |
| docs/rag/full-bank/PHASE_4D4_CROSS_STORE_INTEGRITY.json | 5cec02ca57298409296ef0e3a8325fdece232619b9e007fbb6d5e31b7289bdc2 |
| docs/rag/evaluation/PHASE_4D5_FULL_BANK_GOLDEN_RELEVANCE.json | d4e0fe47c7f103d4fcfb4798345aeeeb8cebdbf09817f015ea6bb30be6a06959 |
| docs/rag/evaluation/PHASE_4D5_FULL_BANK_GOLDEN_REVIEW.md | d0b9ca36c6b7f70a05d14a8c4d7c5106d00551a4ca353080a9e01b23a4b9eb40 |

The Golden and review hashes above are definitive raw-byte hashes. This freeze report is also immutable after creation; its own byte hash is held by the temporary freeze lock and will be copied into baseline results alongside all three before/after hashes. Self-referential hashes are not embedded in their own file.

Pre-registered metrics/interpretation are stored in the Golden and review before retrieval. All 21 queries are positive; DIRECT is relevant; original ranks preserved; no threshold/rerank/refill/dedup/cap/category filter. Exactly one topK=5 production retrieval per query is authorized only after this hard gate passes.

Created three artifacts: full-bank Golden JSON (1092 judgments, exact corpus identities/content and pre-registered metrics); semantic review MD (all 336 new-document pair decisions, evidence and priority adjudications); this immutable freeze report (provenance, hash and gate). No production/config/source/old Golden modification. Offline Maven compile/classpath BUILD SUCCESS; standalone preflight runner compiled and passed. JUnit/Surefire tests=0, failures=0, errors=0, skipped=0; no full-regression claim.

```text
PHASE 4D5A FULL-BANK GOLDEN FREEZE GATE

QUERY_COUNT_21: PASS
CHUNK_COUNT_52: PASS
JUDGMENT_COUNT_1092: PASS
OLD_294_JUDGMENTS_UNCHANGED: PASS
NEW_798_JUDGMENTS_COMPLETE: PASS
ALL_21_QUERIES_HAVE_DIRECT_GOLD: PASS
UNCERTAIN_JUDGMENT_COUNT_0: PASS
NO_RETRIEVAL_EXECUTED: PASS
NO_EMBEDDING_EXECUTED: PASS
NO_MILVUS_SEARCH_EXECUTED: PASS
NO_RERANK_EXECUTED: PASS
CORPUS_UNCHANGED: PASS
QUERY_SET_UNCHANGED: PASS
PHASE_4D5A_GOLDEN_FREEZE: PASS
```
