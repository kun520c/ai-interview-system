// Browser QA against the existing local Mock API. Never use this as real-backend E2E evidence.
import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(path.join(process.env.BROWSER_NODE_MODULES ?? process.cwd(), 'package.json'))
const { chromium } = require('playwright')
const base = process.env.V3_BASE_URL ?? 'http://127.0.0.1:5178'
const api = process.env.V3_MOCK_URL ?? 'http://127.0.0.1:18083'
assert.match(new URL(base).hostname, /^(127\.0\.0\.1|localhost)$/)
assert.match(new URL(api).hostname, /^(127\.0\.0\.1|localhost)$/)
const output = path.resolve(process.env.V3_OUTPUT ?? '../docs/frontend/v3/before')
fs.mkdirSync(output, { recursive: true })
const browser = await chromium.launch({ ...(process.env.V3_CHROME
  ? { executablePath: process.env.V3_CHROME } : { channel: 'chrome' }), headless: true })
const log = { mode: 'REAL_CHROME_MOCK_API', paidModelCalls: 0, captures: [], checks: [], errors: [] }
const widths = [[1440, 900], [1024, 768], [390, 844]]
async function scenario(name) {
  const response = await fetch(`${api}/api/__test__/scenario`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ scenario: name }) })
  assert.equal(response.status, 200)
}
async function openPage(viewport) {
  const context = await browser.newContext({ viewport: { width: viewport[0], height: viewport[1] }, reducedMotion: 'reduce' })
  const page = await context.newPage()
  page.on('pageerror', (error) => log.errors.push(error.message))
  return { context, page }
}
async function login(page, admin = false) {
  await page.goto(`${base}/login`)
  await page.locator('#login-account').fill(admin ? 'fixture.admin' : 'fixture.user')
  await page.locator('#login-password').fill('fixturepass')
  await page.locator('button[type=submit]').click()
  await page.waitForURL(`${base}/`)
  await page.locator('#dashboard-training-title').waitFor()
}
async function capture(page, name, viewport) {
  await page.evaluate(() => document.fonts.ready)
  const metrics = await page.evaluate(() => ({
    width: innerWidth, scrollWidth: document.documentElement.scrollWidth, height: innerHeight, title: document.title,
    overflowingElements: [...document.querySelectorAll('#app *')].filter((element) => {
      if (element.matches('.skip-link') && element !== document.activeElement) return false
      const rect = element.getBoundingClientRect()
      return rect.width > 2 && rect.height > 2 && rect.bottom > 0 && rect.top < innerHeight && (rect.left < -1 || rect.right > innerWidth + 1)
    }).map((element) => `${element.tagName}.${element.className}`).slice(0, 8),
  }))
  assert.equal(metrics.width, viewport[0], `${name}: viewport`)
  assert.ok(metrics.scrollWidth <= metrics.width, `${name}: overflow ${metrics.scrollWidth}`)
  assert.deepEqual(metrics.overflowingElements, [], `${name}: visible elements clipped horizontally`)
  // Visible evidence label belongs to the QA artifact, never to production UI.
  await page.evaluate(() => {
    if (document.querySelector('#qa-mock-label')) return
    const label = document.createElement('div')
    label.id = 'qa-mock-label'
    label.textContent = 'VISUAL QA · MOCK API'
    label.style.cssText = 'position:fixed;bottom:2px;left:4px;z-index:9999;font:9px monospace;background:#fff;color:#475569;padding:2px 5px;border:1px solid #cbd5e1;pointer-events:none'
    document.body.append(label)
  })
  const filename = `${name}-${viewport[0]}.png`
  await page.screenshot({ path: path.join(output, filename) })
  log.captures.push({ filename, viewport, ...metrics })
  process.stdout.write(`${filename}: no horizontal overflow\n`)
}
async function submit(page, text = '先实例化，再依赖注入，然后初始化。结合容器回调顺序说明销毁阶段。') {
  await page.locator('#interview-answer').fill(text)
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
}
async function extraStates(viewport) {
  await scenario('no-active')
  const { page, context } = await openPage(viewport)
  await login(page)
  const envelope = (data) => ({ code: 200, message: 'success', data })
  const empty = { page: 1, pageSize: 10, total: 0, totalPages: 0, items: [] }
  await page.route('**/api/interviews/history*', (route) => route.fulfill({ json: envelope(empty) }))
  await page.reload()
  await page.getByRole('heading', { name: '还没有完成的面试' }).waitFor()
  await capture(page, 'dashboard-empty', viewport)
  let releaseCurrent
  const currentReady = new Promise((resolve) => { releaseCurrent = resolve })
  await page.route('**/api/interviews/current', async (route) => {
    await currentReady
    await route.fulfill({ json: envelope(null) })
  })
  await page.reload()
  await page.locator('.training-skeleton').waitFor()
  await capture(page, 'dashboard-loading', viewport)
  releaseCurrent()
  await page.getByRole('heading', { name: '开始一次模拟面试' }).waitFor()
  await page.unroute('**/api/interviews/current')
  await page.route('**/api/interviews/current', (route) => route.fulfill({ status: 503, json: { code: 503, message: 'Mock temporary outage', data: null } }))
  await page.reload()
  await page.getByRole('heading', { name: '当前面试状态暂时无法加载' }).waitFor()
  await capture(page, 'dashboard-error', viewport)
  await page.unroute('**/api/interviews/current')
  await page.goto(`${base}/interviews`)
  await page.locator('.empty-state').waitFor()
  await capture(page, 'history-empty', viewport)
  await page.goto(`${base}/interviews/new`)
  await page.locator('.difficulty-input').first().waitFor()
  await page.locator('.difficulty-input').first().press('Space')
  assert.equal(await page.locator('input[type=radio]:checked').count(), 1)
  assert.equal(await page.locator('button[type=submit]').isEnabled(), true)
  await capture(page, 'difficulty-keyboard-focus', viewport)
  log.checks.push(`native radio keyboard selection at ${viewport[0]}`)
  let createdSession = null
  await page.route('**/api/interviews', async (route) => {
    const response = await route.fetch()
    const body = await response.json()
    createdSession = body.data
    await route.fulfill({ response })
  })
  await page.route('**/api/interviews/current', (route) => route.fulfill({ json: envelope(createdSession) }))
  await page.locator('button[type=submit]').click()
  await page.waitForURL(`${base}/interview`)
  await page.locator('#interview-answer').waitFor()
  assert.equal(createdSession.difficulty, 'EASY')
  await capture(page, 'session-created', viewport)
  await page.reload()
  await page.locator('#interview-answer').waitFor()
  await capture(page, 'session-resumed', viewport)
  await page.unroute('**/api/interviews')
  await page.unroute('**/api/interviews/current')
  log.checks.push(`create with selected API enum and resume after reload at ${viewport[0]}`)

  let reportState = 'NOT_STARTED'
  let reportPosts = 0
  const ready = await (await fetch(`${api}/api/interviews/41/report`)).json()
  await page.route('**/api/interviews/41/report', async (route) => {
    if (route.request().method() === 'POST') reportPosts++
    const data = reportState === 'READY' ? ready.data : { ...ready.data, reportStatus: reportState, overallScore: null, result: null, summary: null, strengths: null, weaknesses: null, suggestions: null }
    await route.fulfill({ json: envelope(data) })
  })
  await page.goto(`${base}/interviews/41/report`)
  await page.getByRole('button', { name: '生成报告', exact: true }).waitFor()
  assert.equal(reportPosts, 0)
  await capture(page, 'report-not-started', viewport)
  reportState = 'GENERATING'
  await page.getByRole('button', { name: '生成报告', exact: true }).click()
  await page.getByRole('heading', { name: '报告正在生成', exact: true }).waitFor()
  assert.equal(await page.locator('.report-outcome').count(), 0)
  await capture(page, 'report-generating', viewport)
  reportState = 'FAILED'
  await page.getByRole('heading', { name: '上次报告生成未完成' }).waitFor({ timeout: 10000 })
  assert.equal(reportPosts, 1)
  await capture(page, 'report-failed', viewport)
  reportState = 'READY'
  await page.getByRole('button', { name: '重新生成', exact: true }).click()
  await page.locator('.report-outcome').waitFor()
  assert.equal(reportPosts, 2)
  await capture(page, 'report-recovered', viewport)
  log.checks.push(`report not-started → generating → failed → explicit retry → ready at ${viewport[0]}`)
  await page.unroute('**/api/interviews/41/report')

  await scenario('s4')
  const longTechnicalWord = 'ConcurrentHashMap'.repeat(30)
  await page.route('**/api/interviews/current', async (route) => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.currentQuestion.content += `\n${longTechnicalWord}`
    body.data.currentQuestion.knowledgePoint = longTechnicalWord
    await route.fulfill({ json: body })
  })
  await page.goto(`${base}/interview`)
  await page.locator('#interview-answer').waitFor()
  await page.locator('#interview-answer').fill(('原理、边界与实践。' + longTechnicalWord + '\n').repeat(8))
  await page.locator('#interview-answer').scrollIntoViewIfNeeded()
  const dock = await page.locator('.session-dock').boundingBox()
  assert.ok(dock && dock.y >= 0 && dock.y + dock.height <= viewport[1] + 1)
  await capture(page, 'session-long-answer', viewport)
  await page.getByRole('button', { name: '提交答案', exact: true }).click()
  await page.locator('.evaluation-pane').waitFor()
  assert.equal(await page.getByText('ConcurrentHashMap 如何在保证并发安全的同时避免整表锁？', { exact: true }).count(), 0)
  await page.getByRole('button', { name: '下一题', exact: true }).click()
  await page.locator('#interview-answer').waitFor()
  log.checks.push(`long unbroken question / knowledge point / multiline answer and NEXT_MAIN gate at ${viewport[0]}`)
  await page.unroute('**/api/interviews/current')
  await scenario('s5')
  await submit(page)
  await page.getByRole('button', { name: '完成本场并查看报告', exact: true }).waitFor()
  assert.equal(new URL(page.url()).pathname, '/interview')
  await page.reload()
  await page.getByRole('button', { name: '完成本场并查看报告', exact: true }).waitFor()
  await capture(page, 'session-finish-gate', viewport)
  await page.getByRole('button', { name: '完成本场并查看报告', exact: true }).click()
  await page.waitForURL(`${base}/interviews/21/report`)
  await page.locator('.report-outcome').waitFor()
  log.checks.push(`FINISH gate survives reload and requires explicit report navigation at ${viewport[0]}`)
  await page.route('**/api/interviews/history*', (route) => route.fulfill({ status: 401, json: { code: 401, message: 'Mock expired credential', data: null } }))
  await page.goto(`${base}/interviews`)
  await page.waitForURL((url) => url.pathname === '/login')
  await page.locator('#login-account').waitFor()
  await capture(page, 'auth-expired', viewport)
  log.checks.push(`401 returns to login at ${viewport[0]}`)
  await context.close()
}
try {
  for (const viewport of widths) {
    await scenario('no-active')
    const { page, context } = await openPage(viewport)
    const submissions = []
    page.on('request', (request) => {
      if (request.method() === 'POST' && /\/answers$/.test(request.url())) submissions.push(request.postDataJSON())
    })
    await page.goto(`${base}/login`)
    await page.locator('#login-account').waitFor()
    await capture(page, 'login', viewport)
    await page.goto(`${base}/register`)
    await page.locator('#register-account').waitFor()
    await capture(page, 'register', viewport)
    await login(page)
    await capture(page, 'dashboard-start', viewport)
    await page.goto(`${base}/interviews/new`)
    await page.locator('.difficulty-card').first().waitFor()
    assert.equal(await page.locator('input[type=radio]:checked').count(), 0)
    assert.equal(await page.locator('button[type=submit]').isDisabled(), true)
    await capture(page, 'create', viewport)
    await scenario('s3')
    await page.goto(`${base}/`)
    await page.getByRole('heading', { name: '继续当前面试' }).waitFor()
    await capture(page, 'dashboard-resume', viewport)
    await page.goto(`${base}/interview`)
    await page.locator('#interview-answer').waitFor()
    assert.equal(await page.getByRole('button', { name: '提交答案', exact: true }).isDisabled(), true)
    await capture(page, 'session-answering', viewport)
    await scenario('pending')
    await submit(page)
    await page.getByRole('button', { name: '按原答案重新获取评价' }).waitFor()
    assert.equal(await page.locator('#interview-answer').getAttribute('readonly'), '')
    await capture(page, 'session-pending', viewport)
    await page.reload()
    await page.getByRole('button', { name: '按原答案重新获取评价' }).waitFor()
    assert.equal(submissions.length, 1, 'reload must not replay a POST')
    assert.equal(await page.locator('#interview-answer').getAttribute('readonly'), '')
    await scenario('s3')
    await page.getByRole('button', { name: '按原答案重新获取评价' }).click()
    await page.locator('.evaluation-pane').waitFor()
    assert.deepEqual(submissions[0], submissions[1], 'retry keeps the exact requestId and answer')
    assert.equal(await page.locator('#interview-answer').count(), 0)
    await capture(page, 'session-evaluation', viewport)
    if (process.env.V3_EXTRA_CHECKS === 'true') {
      await page.locator('.evaluation-score').scrollIntoViewIfNeeded()
      await capture(page, 'session-feedback-reading', viewport)
      await page.locator('.feedback-block.is-correction').scrollIntoViewIfNeeded()
      const correction = await page.locator('.feedback-block.is-correction').boundingBox()
      const dock = await page.locator('.session-dock').boundingBox()
      assert.ok(correction && dock && correction.y + correction.height <= dock.y + 1, 'correction must not be covered by dock')
      await capture(page, 'session-feedback-bottom', viewport)
      log.checks.push(`feedback bottom clear of dock at ${viewport[0]}`)
    }
    assert.equal(await page.getByText('如果初始化回调抛出异常，容器会如何处理已创建的依赖，以及销毁回调是否仍会执行？', { exact: true }).count(), 0)
    log.checks.push(`future question hidden until explicit continue at ${viewport[0]}`)
    await page.getByRole('button', { name: '继续追问', exact: true }).click()
    await page.locator('#interview-answer').waitFor()
    await capture(page, 'session-followup', viewport)
    await scenario('blocked')
    await submit(page)
    await page.getByText('请不要继续创建新的提交。', { exact: true }).waitFor()
    await capture(page, 'session-blocked', viewport)
    await page.goto(`${base}/interviews/41/report`)
    await page.locator('.report-outcome').waitFor()
    await capture(page, 'report', viewport)
    await page.goto(`${base}/interviews`)
    await page.locator('.history-list').waitFor()
    await capture(page, 'history', viewport)
    if (process.env.V3_EXTRA_CHECKS === 'true' && viewport[0] === 390) {
      await page.getByRole('button', { name: '打开菜单' }).click()
      await page.getByRole('dialog', { name: '导航菜单' }).waitFor()
      await capture(page, 'mobile-menu', viewport)
      await page.keyboard.press('Escape')
      assert.equal(await page.getByRole('dialog', { name: '导航菜单' }).count(), 0)
      assert.equal(await page.getByRole('button', { name: '打开菜单' }).evaluate((element) => element === document.activeElement), true)
      log.checks.push('mobile menu Escape restores focus')
    }
    await page.goto(`${base}/profile`)
    await page.locator('h1').waitFor()
    await capture(page, 'profile', viewport)
    await page.goto(`${base}/admin/questions`)
    await page.waitForURL(`${base}/forbidden`)
    log.checks.push(`USER admin route forbidden at ${viewport[0]}`)
    await context.close()
    await scenario('admin')
    const admin = await openPage(viewport)
    await login(admin.page, true)
    for (const [url, name, selector] of [['/admin/questions', 'admin-questions', '.question-table'], ['/admin/questions/new', 'admin-question-form', '.question-form'], ['/admin/knowledge', 'admin-knowledge', '.upload-form']]) {
      await admin.page.goto(`${base}${url}`)
      await admin.page.locator(selector).waitFor()
      await capture(admin.page, name, viewport)
    }
    await admin.context.close()
  }
  if (process.env.V3_EXTRA_CHECKS === 'true') {
    await extraStates([1440, 900])
    await extraStates([390, 844])
  }
  assert.deepEqual(log.errors, [])
  log.checks.push('0 uncaught browser errors', 'difficulty remains unselected', 'blank answer disabled', 'pending answer readonly', 'explicit follow-up gate', 'all viewports fit')
} catch (error) {
  log.failure = error.stack
  process.exitCode = 1
} finally {
  fs.writeFileSync(path.join(output, 'browser-log.json'), `${JSON.stringify(log, null, 2)}\n`)
  await browser.close()
}
