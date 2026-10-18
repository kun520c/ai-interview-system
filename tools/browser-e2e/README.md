# Windows real-browser acceptance (local Mock AI)

This opt-in harness starts the production Vue app and Spring Boot HTTP application.
It uses a fresh MySQL instance on `127.0.0.1:13326`, the canonical `ai_interview_test`
schema, and the existing schema-only SQL. It never loads `local`, changes the production
schema, or accesses historical RAG vectors, locks, or results.

`BrowserE2eServer.java` is outside `src/` and never ships in the application. Only the
external vector adapter and its startup initializer are replaced. Real embedding and
DeepSeek HTTP adapters call `mock-provider.mjs` on loopback. MyBatis eligibility,
RAG trace persistence, scoring validation, follow-up policy, transactions, JWT,
report aggregation, and weakness persistence remain production code. Every generated
feedback/report explicitly says `本地模拟AI`. No real provider credentials are loaded.

The default historical run directory is `target/browser-e2e/20261018-v3`.
Prepare refuses to overwrite it. Set a new `BROWSER_E2E_RUN_ID` before a future run.

Prerequisites: existing Java 21, MySQL 8 binaries, frontend node_modules,
Playwright and Google Chrome. No tool installation is performed.

Runtime paths come from local environment configuration, never a saved workstation path:

| Variable | Resolution |
|---|---|
| `BROWSER_E2E_JAVA_HOME` | Optional Java 21 override; otherwise `JAVA_HOME` |
| `BROWSER_E2E_MYSQL_BIN` | MySQL bin directory; otherwise `mysqld.exe` on PATH |
| `BROWSER_E2E_NODE` | Optional Node executable; otherwise `node.exe` on PATH |
| `BROWSER_E2E_NODE_MODULES` | Optional absolute node_modules directory containing Playwright; otherwise normal repository package resolution |
| `BROWSER_E2E_CHROME` | Optional Chrome executable; otherwise Playwright's installed `chrome` channel |

The separate Mock API visual checker uses `BROWSER_NODE_MODULES` and `V3_CHROME`
for the equivalent overrides. These are local test settings, not application configuration.

From the repository root, in PowerShell 7 with local process/socket access:

```powershell
./tools/browser-e2e/environment.ps1 -Mode Prepare
./tools/browser-e2e/environment.ps1 -Mode Start
```

Start records process ID, exact creation time and executable path. It binds MySQL,
Mock AI, backend and Vite to loopback (13326, 18097, 18096, 15183). Verify their
startup logs before running `acceptance.mjs` with the bundled Node runtime.
`Services` is a recovery entry point only after schema/fixture initialization.
`RestartBackend` checks all three process identity fields and preserves old logs.

`acceptance.mjs <unique-attempt-name>` launches a dedicated headless Chrome process,
uses actual form input/clicks for all interviews, and closes it in `finally`.
It saves screenshots, redacted HTTP requests/responses, check results and failures.
Non-loopback browser requests are blocked; expected font blocking and deliberately
injected HTTP/transport errors are retained separately from uncaught page errors.
Timeout coverage loses a response **after** a real committed server request, then
replays the original request ID/content through the UI. It does not wait 120 seconds
for Axios's production timeout, or claim to test every physical network failure.

Use `Snapshot` then `verify.mjs` for read-only database/UI consistency checks.
`regression.ps1` runs the full existing Maven suite against this private instance.
It creates/removes a temporary root POM solely for an isolated Surefire report
directory and `-XX:+UseSerialGC -Xmx768m`, following an observed native G1 GC crash.
It clears real-service opt-in switches and never removes or excludes tests.

Finally run `environment.ps1 -Mode Stop`, verify all recorded processes exited,
and retain this run's private `mysql-data` directory and snapshots for review.
The user's original MySQL, Docker and browser sessions must remain running.

The current technical report is `docs/frontend/V3_BLOCKER_FIX_20261018.md`;
`docs/frontend/V3_INTEGRATION_20261018.md` preserves the initial blocked acceptance.
The default acceptance scope is `full`; `happy` explicitly runs only successful
desktop/mobile interview flows and administrator access while a negative-case
failure is being investigated. It must never stand in for a passing `full` run.

For a new acceptance without overwriting the historical directory, set
`$env:BROWSER_E2E_RUN_ID='20261018-v3-b1b2'` in every launcher, regression,
acceptance and snapshot process. IDs accept only 1–64 lowercase letters, digits
or hyphens. The default remains the original historical directory. Ports stay
fixed and must be free before Prepare/Start. The B1/B2 final acceptance report is
`docs/frontend/V3_BLOCKER_FIX_20261018.md`; the earlier report remains historical.
