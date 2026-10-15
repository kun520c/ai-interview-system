# Phase 4D3 production chunk dry-run report

Execution date: 2026-10-06, Asia/Shanghai. Local standalone JDK 21.0.11 runner compiled the byte-unchanged production KnowledgeTextChunker, KnowledgeChunkDraft and BusinessException against the installed actual Spring Component annotation jar. Spring/application services were never started. Python only serialized outputs, checked hashes/substrings and generated artifacts; it did not split text.

The five pilot documents were invoked first exactly once, and their 14 outputs matched both the frozen Phase2C preview and frozen evaluation artifact content/length/SHA-256. Only after that gate did the runner invoke each remaining document once. Total production split calls: 21. Raw source hashes matched the supplied 21 values. All current sources already use normalized UTF-8/LF without BOM, so upload normalization did not change input text.

| Bank ID | Chunks | Minimum | Maximum | Average |
|---|---:|---:|---:|---:|
| E01 | 2 | 460 | 1178 | 819.00 |
| E02 | 2 | 880 | 1139 | 1009.50 |
| E03 | 2 | 1089 | 1104 | 1096.50 |
| E04 | 2 | 930 | 1185 | 1057.50 |
| E05 | 2 | 522 | 1197 | 859.50 |
| M01 | 3 | 450 | 1085 | 838.67 |
| M02 | 3 | 471 | 1109 | 858.67 |
| M03 | 2 | 1081 | 1151 | 1116.00 |
| M04 | 2 | 1142 | 1159 | 1150.50 |
| M05 | 3 | 514 | 1123 | 898.67 |
| M06 | 3 | 496 | 1145 | 848.33 |
| M07 | 3 | 556 | 1193 | 872.33 |
| H01 | 3 | 589 | 1147 | 881.00 |
| H02 | 2 | 954 | 1142 | 1048.00 |
| H03 | 2 | 894 | 1143 | 1018.50 |
| H04 | 2 | 870 | 1159 | 1014.50 |
| H05 | 3 | 551 | 1152 | 879.67 |
| H06 | 3 | 730 | 1064 | 893.67 |
| H07 | 3 | 501 | 1182 | 951.33 |
| H08 | 2 | 744 | 1040 | 892.00 |
| H09 | 3 | 741 | 1137 | 928.33 |

Total: 52 chunks; old pilot: 14; new 16 documents: 38. All lengths are Java UTF-16 units, not tokens. 97/97 points have at least one complete usable passage; all primary excerpts survive. New additional evidence was independently checked, with results in the coverage artifact. No point depends on stitching fragments or on a LOW non-self-contained chunk.

Reference-tail P2 is CONFIRMED_NONBLOCKING. Detailed quality, structural and cross-topic diagnostics are in full-bank/PHASE_4D3_CHUNK_REVIEW.md. P0=0, P1=0, P2=4 consolidated findings.

Production/config/source integrity: 220/220 byte hashes unchanged. The manifest stores the exact before/after inventory including application-local.yaml hash only; credentials are not copied into artifacts. Chunker SHA-256 before/after: c98f2bcc46c14f9ef7216f1e6b42512338ff088d587925e7bef569565d4aa406. Configuration constants are compiled from the unchanged source: 1200/150/800.

Created: full-bank/PHASE_4D3_PRODUCTION_CHUNK_MANIFEST.json (exact 52 chunk texts and metadata); full-bank/PHASE_4D3_SCORING_POINT_CHUNK_COVERAGE.json (97 exact evidence checks); full-bank/PHASE_4D3_CHUNK_REVIEW.md (semantic and severity review); this report (execution and gate). No existing persistent file was modified. Temporary harness sources/classes/helper scripts were removed after the authorized conditional Phase4D4 run.

Verification: standalone production dry-run passed; 21 document operations, 14 frozen matches, 97 point checks, 0 structural hard failures. JUnit/Surefire tests=0, failures=0, errors=0, skipped=0; no full regression claim. MySQL/Milvus/Embedding/HTTP/retrieval/rerank/DeepSeek/Jev calls and DB/vector writes all 0 in Part A.

The hard gate passes, authorizing immediate Part B preflight and ingestion only if the existing runtime matches the frozen pilot. Phase4D5 remains blocked.

```text
PHASE 4D3 PRODUCTION CHUNK DRY-RUN GATE

SOURCE_HASHES_21_21_MATCH: PASS
PRODUCTION_CHUNKER_USED: PASS
PRODUCTION_CHUNKER_UNCHANGED: PASS
PILOT_14_CHUNKS_EXACT_REPRODUCTION: PASS
ALL_21_DOCS_CHUNKED: PASS
ALL_CHUNKS_NONEMPTY: PASS
ALL_CHUNKS_WITHIN_MAX: PASS
SCORING_POINTS_97_97_COVERED: PASS
REFERENCE_TAIL_P2: CONFIRMED_NONBLOCKING
P0_COUNT_0: PASS
P1_COUNT_0: PASS
NO_EXTERNAL_RUNTIME_CALLS: PASS
NO_DB_MILVUS_WRITES: PASS
NO_SOURCE_MUTATION: PASS

PHASE_4D3_OVERALL: PASS
```
