# Phase 2C Offline Chunk Dry-Run Report

## Execution

The dry run invoked the actual production `KnowledgeTextChunker.split(String)` implementation on JDK 21.0.11. The harness compiled the unchanged production `KnowledgeTextChunker`, `KnowledgeChunkDraft`, and `BusinessException` sources; only the Spring `@Component` annotation was stubbed so the class could run without starting Spring. This was a temporary standalone runner, not a Maven/Surefire test run.

Each source was decoded with a strict UTF-8 decoder, one leading BOM was removed if present, and CRLF/CR were converted to LF. No other content was changed. The normalized source lengths match the Phase 2B authoring report. The production splitter returned `characterCount` as Java `String.length()` (UTF-16 code units); these values are not token counts. Start offsets are matched in source order against each returned stripped chunk; end offsets are exclusive.

No source document or application code was edited. No Spring application, database, Milvus, Embedding, DeepSeek, Jev, or RAG service was started or called. The temporary harness and compiled classes will be removed after the reports are complete.

## Summary

```text
DOCUMENT_COUNT: 5
TOTAL_CHUNK_COUNT: 14

M02_CHUNKS: 3
M04_CHUNKS: 2
M05_CHUNKS: 3
H06_CHUNKS: 3
H07_CHUNKS: 3

MIN_CHUNK_LENGTH: 471
MAX_CHUNK_LENGTH: 1182

LOW_RETRIEVAL_VALUE_CHUNKS: 2 (M05-3, H07-3)
NON_SELF_CONTAINED_CHUNKS: 7 (all WARN; see preview audit)
INCOHERENT_BOUNDARIES: 9 (all P2 sentence/clause-start warnings)
BROKEN_CODE_FENCES: 0
SCORING_POINTS_WITHOUT_EVIDENCE: 0
```

Per-document source totals, chunk lengths, offsets, starts/ends, complete chunk text, overlap measurements, and semantic labels are in [PHASE_2C_CHUNK_PREVIEW.md](Phase2C-切片内容预览.md).

## Findings

There are no P0 or P1 findings. Every frozen scoring point has at least one intact HIGH or MEDIUM evidence chunk. The nine boundary warnings are P2: overlap starts at a clipped Chinese sentence/clause fragment, but the chunks retain their dominant subject and complete core explanation. Two short tail chunks are mostly repeated text and official references and have LOW retrieval value. Candidate fix classes are reported only; no fix was applied.

| Document / chunk | Issue and exact evidence | Severity | Smallest candidate fix class |
|---|---|---|---|
| M02-2 | Begins mid-sentence at “key 时更新对应节点…”. Resize and treeification evidence remains complete in the chunk. | P2 | SOURCE_DOCUMENT_EDIT |
| M02-3 | Begins at “64 时…” after the start of the capacity clause; contains a repeated misconception and references. | P2 | SOURCE_DOCUMENT_EDIT |
| M04-2 | Begins at “可能合适；若需求…”, detached from the prior volatile-status example; count++ evidence is complete. | P2 | SOURCE_DOCUMENT_EDIT |
| M05-2 | Begins at “或隐式 otherMethod()…”, the second half of the self-invocation sentence; transaction consequence and remediation remain clear. | P2 | SOURCE_DOCUMENT_EDIT |
| M05-3 | Begins within “JDK dynamic proxy…”, and is mostly repeated misconception text and references; LOW retrieval value. | P2 | SOURCE_DOCUMENT_EDIT |
| H06-2 | Begins at “链提供构造旧版本…”, missing the preceding “undo”; RR and locking-read evidence remains complete. | P2 | SOURCE_DOCUMENT_EDIT |
| H06-3 | Begins after “同一” in an RR sentence; repeats the snapshot/locking boundary and then contains references. | P2 | SOURCE_DOCUMENT_EDIT |
| H07-2 | Begins at “脚本执行 compare-and-delete…”, after the omitted Lua-subject phrase; complete code and explanation remain intact. | P2 | SOURCE_DOCUMENT_EDIT |
| H07-3 | Begins inside the SET phrase at “key token NX PX ttl…”; mostly repeats the version boundary and references; LOW retrieval value. | P2 | SOURCE_DOCUMENT_EDIT |

The H07 1→2 overlap repeats the full Lua compare-and-delete snippet, with both code fences intact. This is duplication within the configured overlap, not a broken code block. No source URL is split across chunks.

## Gate

```text
PHASE 2C OFFLINE CHUNK GATE

REAL_PROJECT_CHUNKER_USED: PASS
DOCUMENT_COUNT_5: PASS
ALL_CHUNKS_NONEMPTY: PASS
BOUNDARIES_COHERENT: PASS
CODE_FENCES_INTACT: PASS
SCORING_POINT_EVIDENCE_COMPLETE: PASS
NO_P0: PASS
NO_UNRESOLVED_P1: PASS
NO_EXTERNAL_SERVICE_CALLS: PASS
NO_DB_WRITES: PASS
NO_SOURCE_DOC_MUTATION: PASS

PHASE_2C_OVERALL: PASS
```
