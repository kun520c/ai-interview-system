import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
const runId = process.env.BROWSER_E2E_RUN_ID ?? '20261018-v3'
if (!/^[a-z0-9-]{1,64}$/.test(runId)) throw new Error('Invalid browser run ID')
const out = path.resolve('target/browser-e2e', runId)
const run = process.argv.find(x => x.startsWith('attempt-')) ?? 'attempt-01'
const afterRegression = process.argv.includes('--after-regression')
const db = JSON.parse(fs.readFileSync(path.join(out, afterRegression
  ? 'database-snapshot-after-regression.json' : 'database-snapshot.json')))
const browser = JSON.parse(fs.readFileSync(path.join(out, run, 'result.json')))
const requests = JSON.parse(fs.readFileSync(path.join(out, run, 'browser-requests.json')))
const checks = []
assert.equal(browser.status, 'PASS')
const by = (table, key, value) => db[table].filter(row => row[key] === value)
for (const session of browser.sessions) {
  const saved = by('interview_session', 'id', session.sessionId)[0]
  assert.equal(saved.status, 'COMPLETED'); assert.equal(saved.report_status, 'READY')
  assert.equal(saved.completed_question_count, 5); assert.equal(saved.current_interview_question_id, null)
  const questions = by('interview_question', 'session_id', session.sessionId)
  assert.equal(questions.filter(q => q.question_type === 'MAIN').length, 5)
  assert.equal(questions.filter(q => q.question_type === 'FOLLOW_UP').length, 2)
  const answers = db.interview_answer.filter(a => questions.some(q => q.id === a.interview_question_id))
  assert.equal(answers.length, 7); assert.ok(answers.every(a => a.status === 'EVALUATED'))
  const evaluations = db.answer_evaluation.filter(e => answers.some(a => a.id === e.answer_id))
  assert.equal(evaluations.length, 7)
  const finals = evaluations.filter(e => e.evaluation_phase === 'FINAL')
  assert.equal(finals.length, 5)
  for (const submit of session.submissions) {
    const answer = answers.find(a => a.request_id === submit.requestId)
    assert.ok(answer)
    const evaluation = evaluations.find(e => e.answer_id === answer.id)
    for (const [wire, sql] of Object.entries({ correctnessScore: 'correctness_score', completenessScore: 'completeness_score',
      depthScore: 'depth_score', clarityScore: 'clarity_score', practiceScore: 'practice_score', totalScore: 'total_score',
      evaluationPhase: 'evaluation_phase', level: 'level' })) assert.equal(evaluation[sql], submit.evaluation[wire])
    assert.equal(evaluation.decision_action, submit.action)
    const batches = by('rag_retrieval_batch', 'answer_id', answer.id)
    assert.equal(batches.length, 1)
    assert.equal(batches[0].raw_hit_count, 5); assert.equal(batches[0].evidence_hit_count, 5)
    assert.ok(!batches[0].query_text.includes('浏览器合成回答'))
    const hits = by('rag_hit_log', 'answer_id', answer.id)
    assert.equal(hits.length, 5)
    assert.ok(hits.every(h => h.retrieval_batch_id === batches[0].retrieval_batch_id))
  }
  const mean = finals.reduce((total, e) => total + e.total_score, 0) / 5
  assert.equal(mean, 70); assert.equal(saved.total_score, mean)
  const reports = by('interview_report', 'session_id', session.sessionId)
  assert.equal(reports.length, 1)
  const report = reports[0]
  assert.equal(report.overall_score, mean); assert.equal(report.summary, session.report.summary)
  for (const field of ['strengths', 'weaknesses', 'suggestions']) assert.deepEqual(JSON.parse(report[field]), session.report[field])
  const weaknesses = by('user_weakness', 'user_id', session.userId)
  assert.equal(weaknesses.length, 2)
  assert.ok(weaknesses.every(w => w.weakness_score === 60 && w.discovered_count === 1))
  assert.equal(requests.filter(r => r.path === `/api/interviews/${session.sessionId}/answers`).length, 7)
  checks.push({ sessionId: session.sessionId, status: 'PASS', mains: 5, followups: 2,
    evaluatedAnswers: 7, finalScores: 5, ragBatches: 7, persistedEvidence: 35, reports: 1, weaknesses: 2, mean })
}
const recovery = browser.recovery
if (browser.scope === 'full') {
  assert.ok(recovery, 'A full acceptance requires recovery evidence')
  const recovered = by('interview_answer', 'request_id', recovery.mainRequest.requestId)
  assert.equal(recovered.length, 1)
  assert.equal(by('answer_evaluation', 'answer_id', recovered[0].id).length, 1)
  assert.equal(by('rag_retrieval_batch', 'answer_id', recovered[0].id).length, 1)
  assert.equal(requests.filter(r => r.body?.requestId === recovery.mainRequest.requestId).length, 2)
  const doubleClick = by('interview_answer', 'request_id', recovery.followupRequest.requestId)
  assert.equal(doubleClick.length, 1)
  assert.equal(by('answer_evaluation', 'answer_id', doubleClick[0].id).length, 1)
  assert.equal(requests.filter(r => r.body?.requestId === recovery.followupRequest.requestId).length, 1)
  const invalid = by('interview_answer', 'request_id', recovery.invalidRequest.requestId)
  assert.equal(invalid.length, 1); assert.equal(invalid[0].status, 'FAILED')
  assert.equal(by('answer_evaluation', 'answer_id', invalid[0].id).length, 0)
  const current = by('interview_session', 'id', recovery.sessionId)[0]
  assert.equal(current.completed_question_count, 1)
  assert.equal(current.current_interview_question_id, recovery.invalidRequest.interviewQuestionId)
  assert.equal(by('interview_report', 'session_id', recovery.sessionId).length, 0)
  assert.equal(by('user_weakness', 'user_id', recovery.userId).length, 0)
  checks.push({ sessionId: recovery.sessionId, status: 'PASS', timeoutReplay: 'one answer/evaluation/batch for two requests',
    doubleClick: 'one request/answer/evaluation', invalidModel: 'FAILED, no evaluation, no advance, no report/weakness' })
}
const result = { status: 'PASS', checks, requests: browser.requestCounts, browserChecks: browser.checks.length,
  scope: browser.scope, fullAcceptancePassed: browser.scope === 'full',
  uncaughtPageErrors: browser.errors.filter(e => e.type === 'pageerror').length }
fs.writeFileSync(path.join(out, afterRegression ? 'consistency-after-regression.json' : 'consistency.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify(result, null, 2))
