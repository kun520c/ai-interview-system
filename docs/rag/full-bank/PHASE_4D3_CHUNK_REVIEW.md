# Phase 4D3 production chunk review

All 52 actual chunks inspected. HIGH requires a complete scoring passage and an understandable mechanism; WARN records clipped leading context independently of complete later body passages. Frozen pilot labels are preserved. No source change was applied.

Coverage: 97/97; LOW: 8; REFERENCES_ONLY: 6; METADATA_HEAVY: 0; selfContained WARN: 26.

| Chunk | UTF-16 | Value | Self-contained | Type | Complete points | Review |
|---|---:|---|---|---|---|---|
| E01-1 | 1178 | HIGH | PASS | BODY | E01-SP1, E01-SP2, E01-SP3, E01-SP4 | Complete scoring evidence: E01-SP1, E01-SP2, E01-SP3, E01-SP4 |
| E01-2 | 460 | MEDIUM | PASS | BODY_PLUS_REFERENCES | — | Useful boundary/misconception context; complete primary evidence survives in the other body chunk. |
| E02-1 | 1139 | HIGH | PASS | MIXED | E02-SP1, E02-SP2, E02-SP3 | Complete scoring evidence: E02-SP1, E02-SP2, E02-SP3 |
| E02-2 | 880 | HIGH | WARN | BODY_PLUS_REFERENCES | E02-SP4 | Complete scoring evidence: E02-SP4 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| E03-1 | 1104 | HIGH | PASS | MIXED | E03-SP1, E03-SP2, E03-SP3 | Complete scoring evidence: E03-SP1, E03-SP2, E03-SP3 |
| E03-2 | 1089 | HIGH | WARN | BODY_PLUS_REFERENCES | E03-SP3, E03-SP4 | Complete scoring evidence: E03-SP3, E03-SP4 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| E04-1 | 1185 | HIGH | PASS | BODY | E04-SP1, E04-SP2, E04-SP3, E04-SP4, E04-SP5 | Complete scoring evidence: E04-SP1, E04-SP2, E04-SP3, E04-SP4, E04-SP5 |
| E04-2 | 930 | HIGH | WARN | BODY_PLUS_REFERENCES | E04-SP4 | Complete scoring evidence: E04-SP4 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| E05-1 | 1197 | HIGH | PASS | BODY | E05-SP1, E05-SP2, E05-SP3, E05-SP4 | Complete scoring evidence: E05-SP1, E05-SP2, E05-SP3, E05-SP4 |
| E05-2 | 522 | MEDIUM | WARN | BODY_PLUS_REFERENCES | — | Useful boundary/misconception context; complete primary evidence survives in the other body chunk. Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| M01-1 | 1085 | HIGH | PASS | BODY | M01-SP1, M01-SP2, M01-SP3 | Complete scoring evidence: M01-SP1, M01-SP2, M01-SP3 |
| M01-2 | 981 | HIGH | WARN | BODY_PLUS_REFERENCES | M01-SP4 | Complete scoring evidence: M01-SP4 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| M01-3 | 450 | LOW | WARN | REFERENCES_ONLY | — | Reference-only tail; no scoring evidence; body support survives independently. Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| M02-1 | 996 | HIGH | PASS | BODY | M02-SP1, M02-SP2, M02-SP3 | Metadata identifies JDK 8u HashMap; table/bin model, lookup, and put path remain together. |
| M02-2 | 1109 | HIGH | WARN | BODY | M02-SP4, M02-SP5 | Starts with the tail “key 时更新…”, but includes load factor, resize threshold, and the full treeification 8/64 boundary. |
| M02-3 | 471 | MEDIUM | WARN | BODY_PLUS_REFERENCES | — | Starts at “64 时…” inside a sentence; it retains the HashMap/JDK 8u correction and references, but adds little beyond chunk 2. |
| M03-1 | 1151 | HIGH | PASS | MIXED | M03-SP1, M03-SP2, M03-SP3, M03-SP4 | Complete scoring evidence: M03-SP1, M03-SP2, M03-SP3, M03-SP4 |
| M03-2 | 1081 | HIGH | PASS | BODY_PLUS_REFERENCES | M03-SP3, M03-SP5 | Complete scoring evidence: M03-SP3, M03-SP5 |
| M04-1 | 1159 | HIGH | PASS | BODY | M04-SP1, M04-SP2 | Volatile synchronization, happens-before, visibility/order, and the distinction from mutual exclusion are intact. |
| M04-2 | 1142 | HIGH | WARN | BODY_PLUS_REFERENCES | M04-SP3, M04-SP4 | Starts with “可能合适…”, then presents the complete count++ lost-update explanation and alternatives. |
| M05-1 | 1059 | HIGH | PASS | BODY | M05-SP1, M05-SP2, M05-SP3 | Proxy model and the self-invocation bypass mechanism are together; Spring 6.2.19 scope is present. |
| M05-2 | 1123 | HIGH | WARN | BODY | M05-SP4 | Starts with “或隐式…”, then contains the default proxy-mode transaction consequence, refactoring fix, and AspectJ boundary. |
| M05-3 | 514 | LOW | PASS | BODY_PLUS_REFERENCES | — | Begins in the middle of the JDK dynamic-proxy/CGLIB sentence and is mostly repeated misconception text and references. |
| M06-1 | 1145 | HIGH | PASS | BODY | M06-SP1, M06-SP2, M06-SP3, M06-SP4, M06-SP5 | Complete scoring evidence: M06-SP1, M06-SP2, M06-SP3, M06-SP4, M06-SP5 |
| M06-2 | 904 | HIGH | WARN | BODY_PLUS_REFERENCES | M06-SP5 | Complete scoring evidence: M06-SP5 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| M06-3 | 496 | LOW | WARN | REFERENCES_ONLY | — | Reference-only tail; no scoring evidence; body support survives independently. Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| M07-1 | 1193 | HIGH | PASS | MIXED | M07-SP1, M07-SP2, M07-SP3, M07-SP4, M07-SP5 | Complete scoring evidence: M07-SP1, M07-SP2, M07-SP3, M07-SP4, M07-SP5 |
| M07-2 | 868 | MEDIUM | WARN | BODY_PLUS_REFERENCES | — | Useful boundary/misconception context; complete primary evidence survives in the other body chunk. Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| M07-3 | 556 | LOW | WARN | REFERENCES_ONLY | — | Reference-only tail; no scoring evidence; body support survives independently. Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| H01-1 | 1147 | HIGH | PASS | BODY | H01-SP1, H01-SP2, H01-SP3, H01-SP4 | Complete scoring evidence: H01-SP1, H01-SP2, H01-SP3, H01-SP4 |
| H01-2 | 907 | HIGH | WARN | BODY_PLUS_REFERENCES | H01-SP4, H01-SP5 | Complete scoring evidence: H01-SP4, H01-SP5 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| H01-3 | 589 | LOW | WARN | REFERENCES_ONLY | — | Reference-only tail; no scoring evidence; body support survives independently. Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| H02-1 | 1142 | HIGH | PASS | MIXED | H02-SP1, H02-SP2, H02-SP3 | Complete scoring evidence: H02-SP1, H02-SP2, H02-SP3 |
| H02-2 | 954 | HIGH | WARN | BODY_PLUS_REFERENCES | H02-SP4, H02-SP5 | Complete scoring evidence: H02-SP4, H02-SP5 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| H03-1 | 1143 | HIGH | PASS | MIXED | H03-SP1, H03-SP2, H03-SP3 | Complete scoring evidence: H03-SP1, H03-SP2, H03-SP3 |
| H03-2 | 894 | HIGH | PASS | BODY_PLUS_REFERENCES | H03-SP4, H03-SP5 | Complete scoring evidence: H03-SP4, H03-SP5 |
| H04-1 | 1159 | HIGH | PASS | BODY | H04-SP1, H04-SP2, H04-SP3 | Complete scoring evidence: H04-SP1, H04-SP2, H04-SP3 |
| H04-2 | 870 | HIGH | WARN | BODY_PLUS_REFERENCES | H04-SP4, H04-SP5 | Complete scoring evidence: H04-SP4, H04-SP5 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| H05-1 | 1152 | HIGH | PASS | MIXED | H05-SP1, H05-SP2, H05-SP4 | Complete scoring evidence: H05-SP1, H05-SP2, H05-SP4 |
| H05-2 | 936 | HIGH | WARN | BODY_PLUS_REFERENCES | H05-SP2, H05-SP3, H05-SP4, H05-SP5 | Complete scoring evidence: H05-SP2, H05-SP3, H05-SP4, H05-SP5 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| H05-3 | 551 | LOW | WARN | REFERENCES_ONLY | — | Reference-only tail; no scoring evidence; body support survives independently. Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| H06-1 | 1064 | HIGH | PASS | BODY | H06-SP1, H06-SP2, H06-SP3 | DB_TRX_ID, DB_ROLL_PTR, undo reconstruction, and the Read View visibility concept are in one chunk. |
| H06-2 | 887 | HIGH | WARN | BODY | H06-SP4, H06-SP5 | Starts with “链提供…”, but carries the RR/RC snapshot distinction and locking-read boundary with their consistent-read context. |
| H06-3 | 730 | MEDIUM | PASS | BODY_PLUS_REFERENCES | — | Starts after “同一” in an RR sentence; it repeats the snapshot/locking boundary and contains the full reference list. |
| H07-1 | 1182 | HIGH | PASS | MIXED | H07-SP1, H07-SP2, H07-SP3 | Redis compatibility baseline, SET NX PX, unique-token ownership, and complete Lua code are present. |
| H07-2 | 1171 | HIGH | WARN | MIXED | H07-SP3, H07-SP4, H07-SP5 | Starts with the tail “脚本执行…”, but includes a complete Lua block, atomic release explanation, TTL limit, and failure boundaries. |
| H07-3 | 501 | LOW | WARN | BODY_PLUS_REFERENCES | — | Starts inside the SET command phrase and mostly repeats the version boundary and official references. |
| H08-1 | 1040 | HIGH | PASS | MIXED | H08-SP1, H08-SP2, H08-SP3 | Complete scoring evidence: H08-SP1, H08-SP2, H08-SP3 |
| H08-2 | 744 | HIGH | WARN | BODY_PLUS_REFERENCES | H08-SP4, H08-SP5 | Complete scoring evidence: H08-SP4, H08-SP5 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| H09-1 | 1137 | HIGH | PASS | MIXED | H09-SP1, H09-SP2, H09-SP3 | Complete scoring evidence: H09-SP1, H09-SP2, H09-SP3 |
| H09-2 | 907 | HIGH | WARN | BODY_PLUS_REFERENCES | H09-SP3, H09-SP4 | Complete scoring evidence: H09-SP3, H09-SP4 Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |
| H09-3 | 741 | LOW | WARN | REFERENCES_ONLY | — | Reference-only tail; no scoring evidence; body support survives independently. Leading overlap/heading/code fragment needs nearby context; later complete body passages remain understandable. |

## Carried reference-tail P2

Character accounting uses source offsets: metadata is before Core model; body spans Core model to Official references; references include the reference heading/list/definitions. UTF-16 counts partition each chunk exactly. Ratio is BODY / REFERENCE.

| Chunk | Length | BODY | REFERENCE | META | BODY/REFERENCE | Value | Required evidence |
|---|---:|---:|---:|---:|---:|---|---|
| M07-2 | 868 | 559 | 309 | 0 | 1.8091 | MEDIUM | False |
| M07-3 | 556 | 0 | 556 | 0 | 0.0000 | LOW | False |
| H01-2 | 907 | 590 | 317 | 0 | 1.8612 | HIGH | True |
| H01-3 | 589 | 0 | 589 | 0 | 0.0000 | LOW | False |
| H09-2 | 907 | 543 | 364 | 0 | 1.4918 | HIGH | True |
| H09-3 | 741 | 0 | 741 | 0 | 0.0000 | LOW | False |

REFERENCE_TAIL_P2: CONFIRMED_NONBLOCKING. M07-3, H01-3 and H09-3 contain only reference material. M07-SP1–SP5 remain complete in M07-1; H01-SP1–SP4 in H01-1 and SP4 alternate/SP5 in H01-2; H09-SP1–SP3 in H09-1 and SP3–SP4 in H09-2. None depends on a LOW tail.

## Structural and overlap findings

Exact duplicate chunks within/across documents: 0/0. Empty or over-limit chunks: 0/0. Code-fence split diagnostics: E02-2, H05-2. E02 and H05 chunk 2 contain a closing fence without its opening fence, while the complete code block survives in chunk 1. This is P2; required prose is intact. Heading/reference-definition/list-line splits and overlap starts are recorded per chunk in the manifest. Reference fragments are never treated as scoring evidence.

## Cross-topic content diagnostics

| Pair | Inspection |
|---|---|
| M02 ↔ H02 | Both discuss bucket arrays, linked/tree bins and put/resize. H02 adds CAS, bucket locks and cooperative transfer; shared vocabulary may compete, but no exact duplicate chunk. |
| M04 ↔ H04 | Both are concurrency topics. Visibility and count++ remain M04; worker/queue/rejection/resource planning remain H04. No shared authored mechanism passage. |
| M05 ↔ H05 | Strongest expected overlap: default proxy mode and self-invocation. H05 adds rollback rules and transaction/resource boundaries; a generic proxy query may return either. |
| E04 ↔ H06 ↔ M06 | InnoDB vocabulary shared. E04 ACID/isolation, H06 visibility/undo/read view and M06 clustered/secondary/covering indexes stay distinct. |
| M07 ↔ H07 | String/SET vocabulary shared; M07 type selection and List/Set semantics differ from H07 NX/PX ownership/atomic unlock. |
| H06/H07 ↔ H09 | FOR UPDATE, final database constraints, retry and unique ownership concepts overlap. H09 remains hypothetical inventory/idempotency; no project inventory implementation claim. |
| E05 ↔ H08 | Connection/reliability/ACK vocabulary shared. E05 transport selection and service semantics differ from H08 handshake/close/TIME_WAIT/CLOSE_WAIT. |

## Severity register

P0=0; P1=0; P2=4 consolidated findings. P2-02 groups existing pilot overlap warnings and newly observed boundary warnings; per-chunk warnings are not additional independent issue IDs. LOW is a quality label, not a severity count.

- P2-01: M07/H01/H09 each produce one reference-only LOW tail; all required points have independent HIGH body evidence.
- P2-02: Production overlap can start inside sentences/headings/reference lines; frozen pilot labels retained, all 97 usable passages survive.
- P2-03: E02-1/E02-2 and H05-1/H05-2 split a code fence at the overlap boundary. Complete code remains in chunk 1 and scoring evidence prose remains complete.
- P2-04: Known cross-topic vocabulary/context overlap; text inspection only, no measured retrieval risk or new Golden set.
