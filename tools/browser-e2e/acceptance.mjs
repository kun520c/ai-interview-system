import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
const require = createRequire(process.env.BROWSER_E2E_NODE_MODULES
  ? path.resolve(process.env.BROWSER_E2E_NODE_MODULES, 'playwright/package.json')
  : path.resolve('package.json'))
const { chromium } = require('playwright')
const BASE = 'http://127.0.0.1:15183'
const runId = process.env.BROWSER_E2E_RUN_ID ?? '20261018-v3'
if (!/^[a-z0-9-]{1,64}$/.test(runId)) throw new Error('Invalid browser run ID')
const OUT = path.resolve('target/browser-e2e', runId)
const RUN = process.argv[2] ?? `run-${Date.now()}`
const scope = process.argv[3] ?? 'full'
assert.ok(['full', 'happy'].includes(scope), 'Unknown acceptance scope')
const dir = path.join(OUT, RUN)
assert.equal(fs.existsSync(dir), false, 'Preserve previous attempts; choose a new attempt name')
fs.mkdirSync(path.join(dir, 'screenshots'), { recursive: true })
const checks = []
const wire = []
const apiRequests = []
const errors = []
const pending = new Set()
let stage = 'launch'
let browser
let activePage
const results = { browser: 'Google Chrome via Playwright, headless, dedicated process/context',
  ai: 'LOCAL_MOCK', paidModelCalls: 0, scope, sessions: [], checks, errors, status: 'RUNNING' }
function check(name, detail = {}) {
  checks.push({ name, status: 'PASS', ...detail })
  console.log(`PASS ${name} ${JSON.stringify(detail)}`)
}
async function shot(page, name) {
  await page.screenshot({ path: path.join(dir, 'screenshots', `${name}.png`), fullPage: true })
}
async function observe(page) {
  fs.writeFileSync(path.join(dir, 'last-page.txt'), await page.locator('body').innerText())
}
async function context(viewport = { width: 1440, height: 1000 }) {
  const ctx = await browser.newContext({ viewport, locale: 'zh-CN' })
  await ctx.route('**/*', route => {
    const url = new URL(route.request().url())
    return ['127.0.0.1', 'localhost'].includes(url.hostname) ? route.continue() : route.abort('blockedbyclient')
  })
  ctx.on('page', page => {
    page.setDefaultTimeout(15000)
    page.on('request', request => {
      const endpoint = new URL(request.url()).pathname
      if (!endpoint.startsWith('/api/')) return
      apiRequests.push({ method: request.method(), path: endpoint,
        hasBearer: Boolean(request.headers().authorization?.startsWith('Bearer ')),
        body: endpoint.endsWith('/answers') ? request.postDataJSON() : undefined })
    })
    page.on('pageerror', error => errors.push({ type: 'pageerror', message: error.message }))
    page.on('console', msg => { if (msg.type() === 'error') errors.push({ type: 'console', message: msg.text() }) })
    page.on('requestfailed', request => wire.push({ type: 'failed', method: request.method(),
      url: request.url(), failure: request.failure(), requestId: request.postDataJSON()?.requestId }))
    page.on('response', response => {
      if (!new URL(response.url()).pathname.startsWith('/api/')) return
      const task = (async () => {
        const request = response.request()
        let body
        try { body = await response.json() } catch { body = null }
        if (body?.data?.accessToken) body = { ...body, data: { ...body.data, accessToken: '[REDACTED]' } }
        let requestBody
        if (/\/answers$/.test(new URL(request.url()).pathname)) requestBody = request.postDataJSON()
        wire.push({ type: 'response', method: request.method(), path: new URL(response.url()).pathname,
          status: response.status(), hasBearer: Boolean(request.headers().authorization?.startsWith('Bearer ')),
          request: requestBody, response: body })
      })()
      pending.add(task); task.finally(() => pending.delete(task))
    })
  })
  return ctx
}
async function signup(page, label) {
  const account = `browser_${label}_${Date.now()}`
  const password = 'BrowserOnly123!'
  await page.goto(`${BASE}/register`)
  await page.locator('#register-account').fill(account)
  await page.locator('#register-username').fill('浏览器隔离验收')
  await page.locator('#register-email').fill(`${account}@example.com`)
  await page.locator('#register-password').fill(password)
  await page.locator('#register-confirm-password').fill(password)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  await page.waitForURL('**/login?**')
  await page.locator('#login-account').fill(account)
  await page.locator('#login-password').fill('WrongPassword123!')
  const bad = page.waitForResponse(r => r.url().endsWith('/api/auth/login'))
  await page.getByRole('button', { name: '登录', exact: true }).click()
  assert.equal((await bad).status(), 400)
  await page.getByText('账号或密码错误', { exact: true }).waitFor()
  await page.getByRole('alert').waitFor()
  check(`${label}:invalid-login-shows-error`)
  await page.locator('#login-password').fill(password)
  const good = page.waitForResponse(r => r.url().endsWith('/api/auth/login'))
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const login = await (await good).json()
  assert.ok(login.data.accessToken)
  const denied = await page.request.get(BASE + '/api/admin/questions?pageNum=1&pageSize=10', {
    headers: { Authorization: `Bearer ${login.data.accessToken}` },
  })
  assert.equal(denied.status(), 403)
  check(`${label}:user-admin-http-403`)
  await page.waitForURL(BASE + '/')
  await page.getByRole('heading', { name: '你好，浏览器隔离验收', exact: true }).waitFor()
  await page.reload()
  await page.getByRole('heading', { name: '你好，浏览器隔离验收', exact: true }).waitFor()
  check(`${label}:register-login-refresh-auth`, { userId: login.data.userId })
  return { account, userId: login.data.userId }
}
async function create(page) {
  await page.goto(`${BASE}/interviews/new`)
  await page.locator('input[value="EASY"]').check()
  const ready = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/api/interviews'))
  await page.getByRole('button', { name: '开始面试', exact: true }).click()
  const response = await ready
  assert.equal(response.status(), 200)
  const data = (await response.json()).data
  assert.equal(data.plannedQuestionCount, 5)
  await page.waitForURL('**/interview')
  await page.locator('#interview-answer').waitFor()
  await page.getByText(data.currentQuestion.content, { exact: true }).first().waitFor()
  return data
}
async function submit(page, sessionId, answer) {
  await page.locator('#interview-answer').fill(answer)
  const response = page.waitForResponse(r => r.url().endsWith(`/api/interviews/${sessionId}/answers`) && r.request().method() === 'POST')
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
  const res = await response
  return { status: res.status(), data: (await res.json()).data, request: res.request().postDataJSON() }
}
async function advance(page, action) {
  const label = { FOLLOW_UP: '继续追问', NEXT_MAIN: '下一题', FINISH: '完成本场并查看报告' }[action]
  assert.ok(label)
  await page.getByRole('button', { name: label, exact: true }).click()
}
async function complete(page, label) {
  const identity = await signup(page, label)
  const created = await create(page)
  const id = created.sessionId
  const item = { label, ...identity, sessionId: id, mains: 0, followups: 0, evaluations: 0, finalEvaluations: 0, submissions: [] }
  results.sessions.push(item)
  check(`${label}:create-five-fixed-main-questions`, { sessionId: id })
  await shot(page, `${label}-first-question`)
  let current = created.currentQuestion
  for (let index = 0; index < 10; index++) {
    await page.getByText(current.content, { exact: true }).first().waitFor()
    await page.locator('#interview-answer').waitFor()
    if (index === 0) {
      await page.locator('#interview-answer').fill('   ')
      assert.equal(await page.getByRole('button', { name: '提交答案', exact: true }).isDisabled(), true)
      check(`${label}:blank-answer-disabled`)
    }
    const answer = `【浏览器合成回答 ${index + 1}】我先说明核心概念，再解释工作机制、实现约定、典型场景及边界情况。这是本地 Mock AI 验收输入。`
    const result = await submit(page, id, answer)
    assert.equal(result.status, 200)
    assert.ok(result.data.evaluation)
    assert.equal(await page.locator('.evaluation-pane .total-number').innerText(), String(result.data.evaluation.totalScore))
    await page.getByText('【本地模拟AI】已表达基本思路。', { exact: true }).first().waitFor()
    item.submissions.push({ questionId: current.interviewQuestionId, type: current.questionType,
      requestId: result.request.requestId, evaluation: result.data.evaluation, action: result.data.nextAction })
    current.questionType === 'MAIN' ? item.mains++ : item.followups++
    item.evaluations++
    if (result.data.evaluation.evaluationPhase === 'FINAL') item.finalEvaluations++
    await shot(page, `${label}-feedback-${index + 1}`)
    if (result.data.nextQuestion) {
      assert.equal(await page.getByText(result.data.nextQuestion.content, { exact: true }).count(), 0)
    }
    check(`${label}:evaluation-awaits-explicit-confirmation-${index + 1}`)
    if (result.data.nextAction === 'FINISH') {
      const before = apiRequests.filter(r => r.path.endsWith('/answers')).length
      await page.reload()
      await page.locator('.evaluation-pane .total-number').waitFor()
      assert.equal(apiRequests.filter(r => r.path.endsWith('/answers')).length, before)
      assert.ok(page.url().endsWith('/interview'))
      check(`${label}:evaluation-confirmation-survives-refresh-${index + 1}`)
    }
    if (result.data.nextAction === 'FINISH') {
      assert.equal(result.data.sessionStatus, 'COMPLETED')
      await advance(page, 'FINISH')
      break
    }
    await advance(page, result.data.nextAction)
    current = result.data.nextQuestion
  }
  assert.equal(item.mains, 5); assert.equal(item.followups, 2)
  assert.equal(item.evaluations, 7); assert.equal(item.finalEvaluations, 5)
  await page.waitForURL(`**/interviews/${id}/report`)
  await page.getByRole('button', { name: '生成报告', exact: true }).waitFor()
  const generating = page.waitForResponse(r => r.url().endsWith(`/api/interviews/${id}/report`) && r.request().method() === 'POST')
  await page.getByRole('button', { name: '生成报告', exact: true }).click()
  const reportResponse = await generating
  assert.equal(reportResponse.status(), 200)
  const report = (await reportResponse.json()).data
  assert.equal(report.overallScore, 70)
  await page.getByText(report.summary, { exact: true }).waitFor()
  await page.getByRole('heading', { name: '薄弱点', exact: true }).waitFor()
  await page.getByText(report.weaknesses[0], { exact: true }).waitFor()
  await shot(page, `${label}-report`)
  await page.reload()
  await page.getByText(report.summary, { exact: true }).waitFor()
  check(`${label}:five-main-two-followup-report-refresh`, { sessionId: id, evaluations: 7, final: 5, score: 70 })
  item.report = report
  await page.getByRole('link', { name: '返回历史', exact: true }).click()
  await page.waitForURL('**/interviews')
  await page.getByRole('link', { name: '查看报告', exact: true }).first().waitFor()
  await shot(page, `${label}-history`)
  check(`${label}:history-view`)
  await page.goto(`${BASE}/interviews/${id}`)
  await page.getByText(answerText(item, 0), { exact: true }).first().waitFor()
  await shot(page, `${label}-detail`)
  check(`${label}:detail-saved-answers`)
  await page.goto(`${BASE}/admin/questions`)
  await page.waitForURL('**/forbidden')
  check(`${label}:user-admin-route-forbidden`)
  const other = results.sessions.find(session => session.userId !== item.userId)
  if (other) {
    const inaccessible = page.waitForResponse(r => r.url().endsWith(`/api/interviews/${other.sessionId}`))
    await page.goto(`${BASE}/interviews/${other.sessionId}/report`)
    assert.equal((await inaccessible).status(), 404)
    await page.getByRole('heading', { name: '面试记录不存在或无权查看', exact: true }).waitFor()
    check(`${label}:other-user-report-hidden-404`)
    await shot(page, `${label}-ownership-error`)
  }
  return item
}
function answerText(item, index) {
  return `【浏览器合成回答 ${index + 1}】我先说明核心概念，再解释工作机制、实现约定、典型场景及边界情况。这是本地 Mock AI 验收输入。`
}
async function administrator(page) {
  await page.goto(BASE + '/login')
  await page.locator('#login-account').fill('browser_v3_admin')
  await page.locator('#login-password').fill('BrowserOnly123!')
  const ready = page.waitForResponse(r => r.url().endsWith('/api/auth/login'))
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const login = (await (await ready).json()).data
  assert.equal(login.role, 'ADMIN')
  const response = await page.request.get(BASE + '/api/admin/questions?pageNum=1&pageSize=10', {
    headers: { Authorization: `Bearer ${login.accessToken}` },
  })
  assert.equal(response.status(), 200)
  assert.equal((await response.json()).data.total, 5)
  const listReady = page.waitForResponse(r => r.url().includes('/api/admin/questions?'))
  await page.goto(BASE + '/admin/questions')
  assert.equal((await listReady).status(), 200)
  await page.getByRole('heading', { name: '题库', exact: true }).waitFor()
  await page.getByText('浏览器验收：equals与hashCode', { exact: true }).waitFor()
  await shot(page, 'admin-question-list')
  await page.goto(BASE + '/admin/knowledge')
  await page.getByRole('heading', { name: '知识文档', exact: true }).waitFor()
  await shot(page, 'admin-knowledge')
  check('admin:normal-login-database-role-http-200-and-management-pages')
}
async function recovery(page) {
  const identity = await signup(page, 'recovery')
  const created = await create(page)
  const id = created.sessionId
  results.recovery = { ...identity, sessionId: id }
  const lost = `**/api/interviews/${id}/answers`
  let original
  // The request reaches the real backend and commits. Only its browser response is lost.
  await page.route(lost, async route => {
    original = route.request().postDataJSON()
    const response = await route.fetch()
    assert.equal(response.status(), 200)
    await route.abort('timedout')
    await page.unroute(lost)
  }, { times: 1 })
  await page.locator('#interview-answer').fill('E2E_SLOW 【浏览器恢复验收】使用原始提交身份恢复结果。')
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
  await page.getByRole('button', { name: '正在评估', exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: '正在评估', exact: true }).isDisabled(), true)
  await page.getByRole('button', { name: '按原答案重新获取评价', exact: true }).waitFor()
  await shot(page, 'recovery-lost-response')
  check('recovery:timeout-is-uncertain-no-new-answer')
  const replay = page.waitForResponse(r => r.url().endsWith(`/api/interviews/${id}/answers`))
  await page.getByRole('button', { name: '按原答案重新获取评价', exact: true }).click()
  const restored = await replay
  assert.equal(restored.status(), 200)
  assert.deepEqual(restored.request().postDataJSON(), original)
  results.recovery.mainRequest = original
  await page.locator('.evaluation-pane').waitFor()
  await shot(page, 'recovery-replayed-feedback')
  await page.reload()
  // Refresh intentionally adopts the authoritative next question; prior feedback stays in detail.
  await page.getByText('上一题评价可在完成后的详情中查看。', { exact: true }).waitFor()
  await shot(page, 'recovery-refresh-next-question')
  check('recovery:same-request-replay-and-refresh-next-question')
  const detailLoad = page.waitForResponse(r => r.url().endsWith(`/api/interviews/${id}`))
  await page.goto(`${BASE}/interviews/${id}`)
  const persisted = (await (await detailLoad).json()).data
  assert.ok(persisted.questions.some(q => q.answer?.answerContent === original.answerContent && q.evaluation))
  await page.waitForURL('**/interview')
  check('recovery:submitted-answer-retained-active-detail-redirects')
  await page.locator('#interview-answer').waitFor()
  await page.reload()
  await page.getByText('【本地模拟AI追问】请进一步解释该机制的边界条件和实际使用场景。', { exact: true }).waitFor()
  check('recovery:refresh-waiting-followup')
  // A bounded delayed provider response leaves the UI visibly locked during repeated clicks.
  await page.locator('#interview-answer').fill('E2E_SLOW 【浏览器重复点击验收】进一步补充边界情况。')
  const completed = page.waitForResponse(r => r.url().endsWith(`/api/interviews/${id}/answers`))
  await page.getByRole('button', { name: '提交答案', exact: true }).dblclick()
  await page.getByRole('button', { name: '正在评估', exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: '正在评估', exact: true }).isDisabled(), true)
  const evaluated = await completed
  assert.equal(evaluated.status(), 200)
  results.recovery.followupRequest = evaluated.request().postDataJSON()
  assert.equal(apiRequests.filter(r => r.body?.requestId === results.recovery.followupRequest.requestId).length, 1)
  assert.equal(apiRequests.filter(r => r.body?.requestId === original.requestId).length, 2)
  await page.locator('.evaluation-pane').waitFor()
  check('recovery:double-click-locked')
  await advance(page, 'NEXT_MAIN')
  const invalid = await submit(page, id, 'E2E_INVALID_MODEL 【浏览器非法模型验收】请安全拒绝超范围分数。')
  results.recovery.invalidRequest = invalid.request
  assert.equal(invalid.status, 502)
  await page.getByText('外部服务暂时不可用，请按原答案重试。', { exact: true }).waitFor()
  await shot(page, 'recovery-invalid-model')
  check('recovery:invalid-model-safe-rejection-502')
  await page.reload()
  await page.getByRole('button', { name: '按原答案重新获取评价', exact: true }).waitFor()
  assert.equal(await page.locator('#interview-answer').inputValue(),
    'E2E_INVALID_MODEL 【浏览器非法模型验收】请安全拒绝超范围分数。')
  check('recovery:failed-answer-retained-after-refresh')
}
try {
  browser = await chromium.launch({ ...(process.env.BROWSER_E2E_CHROME
    ? { executablePath: process.env.BROWSER_E2E_CHROME } : { channel: 'chrome' }), headless: true })
  {
    const main = await context()
    activePage = await main.newPage()
    stage = 'anonymous-protected-page'
    const protectedResponse = await activePage.goto(BASE + '/api/users/me')
    assert.equal(protectedResponse.status(), 401)
    check('anonymous-protected-http-401')
    const adminDenied = await activePage.request.get(BASE + '/api/admin/questions?pageNum=1&pageSize=10')
    assert.equal(adminDenied.status(), 401)
    check('anonymous-admin-http-401')
    await activePage.goto(BASE + '/interview')
    await activePage.waitForURL('**/login?**')
    check('anonymous-protected-page-redirects-login')
    stage = 'complete-main-browser-flow'
    await complete(activePage, 'main')
    await main.close()
  }
  if (scope === 'full') {
    const recoveryContext = await context()
    activePage = await recoveryContext.newPage()
    stage = 'recovery-and-negative-cases'
    await recovery(activePage)
    await recoveryContext.close()
  }
  const finalContext = await context({ width: 390, height: 844 })
  activePage = await finalContext.newPage()
  stage = 'repeat-complete-browser-flow'
  await complete(activePage, 'final')
  await finalContext.close()
  const adminContext = await context()
  activePage = await adminContext.newPage()
  stage = 'administrator-permissions'
  await administrator(activePage)
  await adminContext.close()
  const unexpected = errors.filter(e => e.type === 'pageerror' ||
    (!/Failed to load resource:.*(400|401|404|502|ERR_TIMED_OUT|ERR_BLOCKED_BY_CLIENT)/.test(e.message)))
  assert.deepEqual(unexpected, [])
  results.status = 'PASS'
} catch (error) {
  results.status = 'FAIL'
  results.failure = { stage, message: error.message, stack: error.stack }
  console.error(error.stack)
  if (activePage && !activePage.isClosed()) {
    await shot(activePage, 'failure').catch(() => {})
    await observe(activePage).catch(() => {})
  }
  process.exitCode = 1
} finally {
  await Promise.allSettled([...pending])
  fs.writeFileSync(path.join(dir, 'browser-network.json'), JSON.stringify(wire, null, 2))
  fs.writeFileSync(path.join(dir, 'browser-requests.json'), JSON.stringify(apiRequests, null, 2))
  results.requestCounts = { allApi: apiRequests.length, answers: apiRequests.filter(r => r.path.endsWith('/answers')).length,
    reportGeneration: apiRequests.filter(r => r.method === 'POST' && r.path.endsWith('/report')).length }
  fs.writeFileSync(path.join(dir, 'result.json'), JSON.stringify(results, null, 2))
  await browser?.close()
  console.log(`BROWSER_E2E=${results.status} ${dir}`)
}
