# Offline verification and historical experiments

Production RAG is configured in `src/main`; the default COSINE threshold switch remains disabled. Classes under this directory are standalone experiments and are not registered as production Spring strategies.

The portable public regression entry points are:

```powershell
.\mvnw.cmd -B -ntp "-Dtest=RagP1OfflineBusinessReplayTest,RagP1IndependentQuerySetTest,KnowledgeRetrievalThresholdTest" test
python -B -m unittest discover -s tools/rag-p1 -p test_replay.py
```

Both use version-controlled synthetic inputs and require no model or database access. The Maven test resources include a separate authored `public-replay.json`; no original paid response, execution lock or private provenance file is required.

After Maven dependencies are cached, run the complete portable gate with Java 21 in `JAVA_HOME`:

```powershell
python -B tools/run_public_offline.py
```

It runs the RAG Java/Python tests and independently compiles the authored candidate-gate Mock cases. Child processes receive only runtime/cache environment variables. New output uses a unique `target/public-offline-<uuid>/` directory. No frozen manifest, execution lock, database or Milvus is read or written.

Other P1/P2/P3/P4/P4.1/P4.2, candidate-gate, E2E programs preserve historical protocols and source for review. Their historical preparation/replay tests may require private artifacts under `target/`, cached classpaths, resource identities or archived evidence. They are not part of the portable regression gate, and a fresh clone cannot reproduce unbundled historical evidence. Do not fabricate replacements for their frozen hashes or treat a missing archive as permission to rerun a provider.

`rag-e2e/contract.json`, `rag-e2e/recovery-plan.json`, original locks, database exports, request/response logs and runtime reports stay local. Source schema SQL and password-marker provisioning templates are separate from database data backups.

Paid execution entries retain their original explicit authorization and budget guards. Publishing their source does not authorize execution. Historical FAIL, STOPPED, BLOCKED and PENDING results remain unchanged; offline Mock success does not establish semantic quality or production readiness.

J1 (2026-11-11): production and active E2E scoring now use only DeepSeek. `final-e2e` forwards only Embedding and DeepSeek, with one DeepSeek attempt per answer; `rag-e2e` and `browser-e2e` no longer set a provider selector. Historical zero-call counters and archived Jev usage readers remain read-only compatibility fields. The [Jev network experiment](jev-proxy-network/README.md) is retired; its launcher and the old evaluation reachability/report-generation scripts fail immediately without networking or rewriting old reports.

For the J1 Java tool compile/offline contract gate, run `python3 tools/check_deepseek_only.py` after the Maven test compile. It uses synthetic data, no model credentials, no database and no historical target artifacts. It also statically checks PowerShell selectors; actually executing Windows PowerShell launchers still requires Windows.
