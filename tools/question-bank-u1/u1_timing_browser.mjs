import { randomBytes } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { setTimeout as wait } from 'node:timers/promises'

const frontend = `http://127.0.0.1:${Number(process.env.U1_TIMING_FRONTEND_PORT)}`
const cdp = `http://127.0.0.1:${Number(process.env.U1_TIMING_CDP_PORT)}`
const key = process.env.U1_TIMING_BROWSER_KEY
const output = 'backups/U1_20261114/timing-browser.json'
const bank = JSON.parse(readFileSync('docs/question-bank/D1_QUESTION_BANK_V2_20261114.json', 'utf8')).questions
const bankByKnowledgePoint = new Map(bank.filter(item => item.difficulty === 'EASY')
  .map(item => [item.knowledgePoint, { answer: item.referenceAnswer, points: item.scoringPoints.map(p => p.content) }]))
const modified = JSON.parse(readFileSync('docs/question-bank/U1_QUESTION_CONTENT_CANDIDATES_20261114.json', 'utf8'))
const results = { status: 'STOPPED', browserMeasurement: 'Chrome, application UI; CDP request-to-body finish, click-to-visible DOM after two animation frames', answers: [] }
const require = (condition, reason) => { if (!condition) throw new Error(reason) }
const normalize = value => value.replace(/\s+/g, ' ').trim()

async function retryRead(action, seconds, reason) {
  const timeout = performance.now() + seconds * 1000
  while (performance.now() < timeout) {
    const result = await action()
    if (result) return result
    await wait(100)
  }
  throw new Error(reason)
}

async function run() {
  require(key?.length >= 32, 'BROWSER_RUN_KEY')
  const tab = await retryRead(async () => {
    try {
      const tabs = await (await fetch(`${cdp}/json/list`)).json()
      return tabs.find(item => item.type === 'page' && item.url === 'about:blank')
    } catch { return null }
  }, 12, 'CHROME_NOT_READY')
  const socket = new WebSocket(tab.webSocketDebuggerUrl)
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject })
  let sequence = 0
  const pending = new Map()
  const network = new Map()
  const observed = kind => [...network.values()].filter(item => item.kind === kind)
  socket.onmessage = ({ data }) => {
    const message = JSON.parse(data)
    if (message.id && pending.has(message.id)) {
      pending.get(message.id)(message)
      pending.delete(message.id)
    }
    const params = message.params
    if (message.method === 'Network.requestWillBeSent') {
      const url = new URL(params.request.url)
      if (params.request.method === 'POST' && url.origin === frontend && /^\/api\/interviews\/\d+\/(answers|report)$/.test(url.pathname)) {
        network.set(params.requestId, { kind: url.pathname.endsWith('/report') ? 'report' : 'answer', start: params.timestamp })
      }
    }
    if (params?.requestId && network.has(params.requestId)) {
      const item = network.get(params.requestId)
      if (message.method === 'Network.responseReceived') {
        item.status = params.response.status
        item.timingId = Object.entries(params.response.headers).find(([name]) => name.toLowerCase() === 'x-interview-timing-id')?.[1]
        item.headersMs = (params.timestamp - item.start) * 1000
      }
      if (message.method === 'Network.loadingFinished') item.httpMs = (params.timestamp - item.start) * 1000
      if (message.method === 'Network.loadingFailed') item.failed = true
    }
  }
  const command = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP_TIMEOUT')) }, 100000)
    pending.set(id, response => {
      clearTimeout(timer)
      if (response.error) reject(new Error('CDP_ERROR'))
      else resolve(response.result)
    })
    socket.send(JSON.stringify({ id, method, params }))
  })
  const js = async (source, ...args) => {
    const expression = `(${source.toString()})(...${JSON.stringify(args)})`
    const result = await command('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
    if (result.exceptionDetails) throw new Error('BROWSER_EVALUATION_FAILED')
    return result.result.value
  }
  const navigate = path => command('Page.navigate', { url: frontend + path })
  const button = async label => js(text => {
    const found = [...document.querySelectorAll('button')].find(el => el.innerText.trim() === text && !el.disabled)
    if (!found) return false
    found.click()
    return true
  }, label)
  const clickTiming = async (label, selector, waitingText) => js((text, target, waiting) => {
    const found = [...document.querySelectorAll('button')].find(el => el.innerText.trim() === text && !el.disabled)
    if (!found) return false
    const started = performance.now()
    window.u1Timing = {}
    const observer = new MutationObserver(() => {
      const notice = [...document.querySelectorAll('[role=status]')]
        .find(el => el.innerText.includes(waiting) && el.getClientRects().length)
      if (notice && window.u1Timing.waitingVisibleMs === undefined) {
        window.u1Timing.waitingVisibleMs = performance.now() - started
      }
      if (!document.querySelector(target)?.getClientRects().length) return
      observer.disconnect()
      requestAnimationFrame(() => requestAnimationFrame(() => { window.u1Timing.resultVisibleMs = performance.now() - started }))
    })
    observer.observe(document.body, { childList: true, subtree: true, characterData: true })
    found.click()
    return true
  }, label, selector, waitingText)
  const backendCall = async (path, body) => js(async (url, payload) => {
    const credential = JSON.parse(localStorage.getItem('interview.auth.credential') || '{}')
    const response = await fetch(url, {
      method: payload ? 'POST' : 'GET',
      headers: { 'Content-Type': 'application/json', ...(credential.accessToken ? { Authorization: 'Bearer ' + credential.accessToken } : {}) },
      body: payload ? JSON.stringify(payload) : undefined,
    })
    const result = await response.json()
    if (response.ok && result.code === 200) return result.data ?? true
    return null
  }, path, body)
  const record = async (kind, index) => {
    const timing = await retryRead(() => {
      const item = observed(kind)[index]
      return item && (item.httpMs !== undefined || item.failed) ? item : null
    }, 95, 'MUTATION_NETWORK_TIMEOUT')
    const { start, ...row } = timing
    const target = kind === 'answer' ? results.answers : results.reports
    target.push(row)
    require(row.status === 200 && row.timingId && !row.failed, 'MUTATION_HTTP_FAILED')
    const presentation = await retryRead(() => js(() => window.u1Timing?.resultVisibleMs === undefined ? null : window.u1Timing),
      8, 'RESULT_NOT_VISIBLE')
    row.clickToVisibleMs = presentation.resultVisibleMs
    row.clickToWaitingNoticeDomMs = presentation.waitingVisibleMs ?? null
  }

  try {
    await command('Page.enable')
    await command('Network.enable')
    // Add the run key only to same-origin /api requests from the fresh synthetic browser profile.
    await command('Page.addScriptToEvaluateOnNewDocument', { source: `(() => {
      const key = ${JSON.stringify(key)};
      const origin = ${JSON.stringify(frontend)};
      const localApi = url => { const u = new URL(url, location.href); return u.origin === origin && u.pathname.startsWith('/api/'); };
      const originalOpen = XMLHttpRequest.prototype.open;
      XMLHttpRequest.prototype.open = function(method, url, ...rest) {
        originalOpen.call(this, method, url, ...rest);
        if (localApi(url)) this.setRequestHeader('X-U1-Synthetic-Run', key);
      };
      const originalFetch = window.fetch;
      window.fetch = (url, options = {}) => {
        if (!localApi(url)) return originalFetch(url, options);
        const headers = new Headers(options.headers);
        headers.set('X-U1-Synthetic-Run', key);
        return originalFetch(url, {...options, headers});
      };
    })();` })
    await navigate('/login')
    await retryRead(() => js(() => Boolean(document.querySelector('#login-account'))), 15, 'APP_LOGIN_NOT_READY')
    const account = `u1timing_${randomBytes(5).toString('hex')}`
    const credentials = { account, password: randomBytes(20).toString('hex') }
    require(await backendCall('/api/auth/register', { ...credentials, username: 'U1 timing synthetic', email: account + '@example.test' }), 'REGISTER_FAILED')
    const loggedIn = await backendCall('/api/auth/login', credentials)
    require(loggedIn?.accessToken && loggedIn.role === 'USER', 'LOGIN_FAILED')
    await js(value => localStorage.setItem('interview.auth.credential', JSON.stringify({ accessToken: value.accessToken,
      tokenType: value.tokenType, expiresAt: Date.now() + value.expiresInSeconds * 1000, userId: value.userId })), loggedIn)
    await navigate('/interviews/new')
    await retryRead(() => js(() => {
      const option = document.querySelector('input[type=radio][value=EASY]')
      if (!option) return false
      option.click()
      return true
    }), 20, 'EASY_NOT_VISIBLE')
    require(await button('开始面试'), 'EASY_START_MISSING')
    await retryRead(() => js(() => Boolean(document.querySelector('#interview-answer'))), 20, 'ANSWER_INPUT_NOT_VISIBLE')
    const first = await backendCall('/api/interviews/current')
    require(first?.plannedQuestionCount === 5 && first.difficulty === 'EASY', 'EASY_PLAN')
    results.sessionId = first.sessionId
    results.userId = loggedIn.userId
    results.reports = []
    const seen = new Set()
    for (let count = 0; count < 7; count++) {
      const current = await backendCall('/api/interviews/current')
      require(current?.sessionId === results.sessionId && current.currentQuestion?.interviewQuestionId, 'CURRENT_STATE')
      const question = current.currentQuestion
      const fixture = bankByKnowledgePoint.get(question.knowledgePoint)
      require(fixture && !seen.has(question.interviewQuestionId), 'ANSWER_FIXTURE_OR_REPLAY')
      seen.add(question.interviewQuestionId)
      const candidate = modified.questions?.find(item => item.knowledgePoint === question.knowledgePoint)
      const text = appendContext(candidate?.candidateRequest?.referenceAnswer || fixture.answer, fixture.points, question.questionType)
      require(await js(answer => {
        const input = document.querySelector('#interview-answer')
        if (!input || input.readOnly) return false
        input.value = answer
        input.dispatchEvent(new Event('input', { bubbles: true }))
        return true
      }, text), 'ANSWER_INPUT_LOCKED')
      await retryRead(() => clickTiming('提交答案', '.evaluation-pane', '正在评估你的回答'), 2, 'SUBMIT_BUTTON_MISSING')
      await record('answer', count)
      Object.assign(results.answers.at(-1), { kind: question.questionType, knowledgePoint: question.knowledgePoint })
      const next = await js(() => [...document.querySelectorAll('button')].map(el => el.innerText.trim())
        .find(label => ['继续追问', '下一题', '完成本场并查看报告'].includes(label)))
      require(next, 'ADVANCE_BUTTON_MISSING')
      if (next === '完成本场并查看报告') {
        require(await button(next), 'ADVANCE_TO_REPORT_FAILED')
        await retryRead(() => clickTiming('生成报告', '.report-summary', '正在生成报告'), 14, 'REPORT_BUTTON_MISSING')
        await record('report', 0)
        break
      }
      require(count < 6, 'ANSWER_CAP_BEFORE_REPORT')
      require(await button(next), 'ADVANCE_FAILED')
      await retryRead(() => js(() => Boolean(document.querySelector('#interview-answer'))), 12, 'NEXT_ANSWER_INPUT_MISSING')
    }
    require(results.reports.length === 1, 'ANSWER_CAP_BEFORE_REPORT')
    const report = await backendCall(`/api/interviews/${results.sessionId}/report`)
    const detail = await backendCall(`/api/interviews/${results.sessionId}`)
    const history = await backendCall('/api/interviews/history?page=1&pageSize=10')
    const main = results.answers.filter(item => item.kind === 'MAIN').length
    require(report?.reportStatus === 'READY' && detail?.status === 'COMPLETED' && main === 5
      && (history?.records ?? history?.items)?.some(item => item.sessionId === results.sessionId), 'REPORT_READBACK')
    results.status = 'COMPLETED'
    results.mainCount = main
    results.followUpCount = results.answers.length - main
  } finally {
    results.observedMutationRequests = Object.fromEntries(['answer', 'report'].map(kind => [kind,
      observed(kind).map(({start, ...item}) => item)]))
    socket.close()
  }
}

function appendContext(answer, points, kind) {
  if (kind === 'MAIN') return answer
  return normalize(`${answer}\n在这一题的条件下，还需要明确以下约束：${points.join('；')}`)
}

try {
  await run()
} catch (error) {
  results.errorCode = /^([A-Z_]{3,40})$/.test(error?.message) ? error.message : 'BROWSER_STOPPED'
  process.exitCode = 1
} finally {
  writeFileSync(output, JSON.stringify(results, null, 2) + '\n', { flag: 'wx', mode: 0o600 })
  console.log(`U1 browser ${results.status}, answer POSTs observed=${results.answers.length}, report POSTs observed=${results.reports?.length || 0}`)
}
