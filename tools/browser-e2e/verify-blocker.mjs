import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'

// Read-only diagnosis of the preserved failed acceptance, not a passing acceptance gate.
const out = path.resolve('target/browser-e2e/20261018-v3')
const run = JSON.parse(fs.readFileSync(path.join(out, 'attempt-02/result.json')))
const requests = JSON.parse(fs.readFileSync(path.join(out, 'attempt-02/browser-requests.json')))
const network = JSON.parse(fs.readFileSync(path.join(out, 'attempt-02/browser-network.json')))
const db = JSON.parse(fs.readFileSync(path.join(out, 'database-snapshot.json')))
assert.equal(run.status, 'FAIL')
assert.match(run.failure.message, /500 !== 502/)
const recovery = run.recovery
const invalid = db.interview_answer.filter(a => a.request_id === recovery.invalidRequest.requestId)
assert.equal(invalid.length, 1)
assert.equal(invalid[0].status, 'FAILED')
assert.equal(db.answer_evaluation.filter(e => e.answer_id === invalid[0].id).length, 0)
const session = db.interview_session.find(s => s.id === recovery.sessionId)
assert.equal(session.status, 'IN_PROGRESS')
assert.equal(session.completed_question_count, 1)
assert.equal(session.current_interview_question_id, recovery.invalidRequest.interviewQuestionId)
assert.equal(db.interview_report.filter(r => r.session_id === session.id).length, 0)
assert.equal(db.user_weakness.filter(w => w.user_id === recovery.userId).length, 0)
const response = network.find(r => r.request?.requestId === recovery.invalidRequest.requestId)
assert.equal(response.status, 500)
assert.equal(response.response.code, 500)
assert.equal(response.response.message, '系统异常，请稍后重试')
for (const [name, expectedRequests] of [['mainRequest', 2], ['followupRequest', 1]]) {
  const saved = db.interview_answer.filter(a => a.request_id === recovery[name].requestId)
  assert.equal(saved.length, 1)
  assert.equal(db.answer_evaluation.filter(e => e.answer_id === saved[0].id).length, 1)
  assert.equal(db.rag_retrieval_batch.filter(b => b.answer_id === saved[0].id).length, 1)
  assert.equal(requests.filter(r => r.body?.requestId === recovery[name].requestId).length, expectedRequests)
}
const source = 'src/main/java/com/kun/aiinterview/interview/orchestration/EvaluationOrchestrationService.java'
const published = execFileSync('git', ['show', `d0b7d59:${source}`])
const actual = fs.readFileSync(source).toString().replaceAll('\r\n', '\n')
assert.equal(actual, published.toString())
const result = {
  diagnosis: 'KNOWN_BLOCKER_REPRODUCED', readiness: 'NOT_READY_TO_MERGE',
  expectedHttpStatus: 502, actualHttpStatus: 500,
  message: response.response.message, sessionId: session.id, answerId: invalid[0].id,
  databaseSafety: 'PASS: failed answer, no evaluation, no workflow advance, no report or weakness',
  replayAndDoubleClick: 'PASS: one durable answer/evaluation/retrieval batch for each request identity',
  sourceUnchangedFromMain: true,
  sourceSha256LF: createHash('sha256').update(published).digest('hex'),
  backendFixApplied: false,
  userDecision: 'Keep main backend unchanged; classify as merge blocker',
}
fs.writeFileSync(path.join(out, 'known-blocker.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify(result, null, 2))
