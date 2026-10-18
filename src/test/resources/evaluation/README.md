# Public offline evaluation fixtures

`jev-reliability-synthetic-scenarios.json` is an authored protocol test matrix.
`JevE06ReliabilityReplayTest` builds a minimal synthetic context and request-driven
Jev rubric, and reuses `JevFeedbackReliabilityTest.structuralFeedback` for valid
feedback. All HTTP requests are intercepted by `MockRestServiceServer`.

The matrix covers native decisions, valid feedback, feedback retry disabled,
successful retry, exhausted retry with DeepSeek fallback, invalid native output,
and invalid fallback scoring. Assertions count actual native/feedback/fallback
requests and verify the native result is retained through feedback retry.
It is not a historical replay or an accuracy benchmark. Retry settings remain
the production-supported 0 and 1; no retries are added to the transport.

The existing `jev-e05-numeric-captures.json` and `jev-e06-feedback-captures.json`
continue to support their original regression tests without alteration. They
are not copied into the new matrix or relabelled as newly generated data.

Historical research records such as the E06 33/38 statistics depend on the
original private `PHASE_5B_EPOCH_06_RUN_EVIDENCE.json` archive. That archive stays
outside Git. Default Maven tests neither read nor require it, and this synthetic
test suite does not independently reproduce its statistics, model quality,
real latency, usage or charges. Historical reports and evidence remain preserved.
