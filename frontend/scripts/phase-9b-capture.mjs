import fs from 'node:fs'
import path from 'node:path'
import puppeteer from 'puppeteer-core'

const BASE = process.env.PHASE_9B_BASE_URL ?? 'http://127.0.0.1:5173'
const OUT = process.env.PHASE_9B_SHOT_DIR ?? path.resolve(
  'e:/learning/IDEA_PROJECTJ/ai-interview-system/docs/frontend/review/final/screenshots',
)
const CHROME =
  process.env.PHASE_9B_CHROME ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const LOG_PATH = path.join(path.dirname(OUT), 'screenshot-viewport-log.json')

const USER = { account: 'fixture.user', password: 'fixturepass' }
const ADMIN = { account: 'fixture.admin', password: 'fixturepass' }
const ANSWER = '先实例化，再依赖注入，然后初始化。结合容器回调顺序说明销毁阶段。'

fs.mkdirSync(OUT, { recursive: true })

const log = []

async function setScenario(page, scenario) {
  const result = await page.evaluate(async (next) => {
    const response = await fetch('/api/__test__/scenario', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ scenario: next }),
    })
    return response.json()
  }, scenario)
  if (result?.code !== 200) {
    throw new Error(`scenario ${scenario} failed: ${JSON.stringify(result)}`)
  }
}

async function clearSite(page) {
  await page.goto(`${BASE}/login`, { waitUntil: 'load' })
  await page.evaluate(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
}

async function login(page, credential) {
  await page.goto(`${BASE}/login`, { waitUntil: 'load' })
  await page.waitForSelector('#login-account')
  await page.click('#login-account', { clickCount: 3 })
  await page.type('#login-account', credential.account)
  await page.click('#login-password', { clickCount: 3 })
  await page.type('#login-password', credential.password)
  await Promise.all([
    page.waitForFunction(() => location.pathname !== '/login', { timeout: 15_000 }),
    page.click('button[type="submit"]'),
  ])
}

async function logout(page) {
  await page.evaluate(() => {
    const button = [...document.querySelectorAll('button')].find((el) =>
      el.textContent?.includes('退出登录'),
    )
    button?.click()
    localStorage.clear()
    sessionStorage.clear()
  })
}

async function setViewport(page, width, height, mobile) {
  await page.setViewport({
    width,
    height,
    deviceScaleFactor: 1,
    isMobile: Boolean(mobile),
    hasTouch: Boolean(mobile),
  })
}

async function metrics(page) {
  return page.evaluate(() => ({
    innerWidth: window.innerWidth,
    innerHeight: window.innerHeight,
    scrollWidth: document.documentElement.scrollWidth,
    title: document.title,
    href: location.href,
  }))
}

async function capture(page, filename, { width, height, mobile = false, waitFor, scrollSelector } = {}) {
  await setViewport(page, width, height, mobile)
  await new Promise((r) => setTimeout(r, 350))
  if (waitFor) {
    await page.waitForFunction(waitFor, { timeout: 15_000 })
  }
  if (scrollSelector) {
    await page.evaluate((sel) => {
      const el =
        typeof sel === 'string'
          ? [...document.querySelectorAll('body *')].find((node) => node.textContent?.includes(sel))
          : null
      el?.scrollIntoView({ block: 'center' })
    }, scrollSelector)
    await new Promise((r) => setTimeout(r, 200))
  }
  const m = await metrics(page)
  if (width === 390) {
    if (m.innerWidth !== 390) {
      throw new Error(`${filename}: innerWidth=${m.innerWidth}, expected 390`)
    }
    if (m.scrollWidth > 390) {
      throw new Error(`${filename}: scrollWidth=${m.scrollWidth} exceeds 390`)
    }
  }
  const dest = path.join(OUT, filename)
  await page.screenshot({ path: dest, fullPage: false })
  log.push({ filename, ...m, viewport: { width, height, mobile } })
  process.stdout.write(
    `${filename} innerWidth=${m.innerWidth} scrollWidth=${m.scrollWidth} title=${m.title}\n`,
  )
}

async function submitAnswer(page) {
  await page.waitForSelector('#interview-answer:not([readonly])', { timeout: 10_000 })
  await page.click('#interview-answer', { clickCount: 3 })
  await page.type('#interview-answer', ANSWER)
  await page.waitForFunction(() => {
    const button = [...document.querySelectorAll('button')].find((el) => el.textContent?.includes('提交答案'))
    return button && !button.disabled
  })
  await page.evaluate(() => {
    const button = [...document.querySelectorAll('button')].find((el) => el.textContent?.includes('提交答案'))
    button?.click()
  })
}

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: true,
  args: ['--disable-gpu', '--hide-scrollbars', '--font-render-hinting=none'],
  defaultViewport: null,
})

try {
  const page = await browser.newPage()
  page.setDefaultTimeout(20_000)

  // --- 1440 user surfaces ---
  await setViewport(page, 1440, 900, false)
  await clearSite(page)
  await login(page, USER)

  await setScenario(page, 'answering')
  await page.goto(`${BASE}/`, { waitUntil: 'load' })
  await capture(page, 'dashboard-ready-null-1440.png', {
    width: 1440,
    height: 900,
    scrollSelector: '报告数据暂时异常',
  })

  await setScenario(page, 'no-active')
  await page.goto(`${BASE}/interviews/new`, { waitUntil: 'load' })
  await capture(page, 'create-1440.png', { width: 1440, height: 900, waitFor: () => document.body.innerText.includes('选择难度') || document.body.innerText.includes('未完成的面试') })

  await setScenario(page, 'answering')
  await page.goto(`${BASE}/interview`, { waitUntil: 'load' })
  await page.waitForSelector('.question-content')
  await capture(page, 'session-answering-1440.png', {
    width: 1440,
    height: 900,
    waitFor: () => document.body.innerText.includes('提交答案'),
  })

  await setScenario(page, 'pending')
  await submitAnswer(page)
  await page.waitForFunction(() => document.body.innerText.includes('按原答案重新获取评价'))
  await capture(page, 'session-pending-warning-1440.png', {
    width: 1440,
    height: 900,
    waitFor: () => document.body.innerText.includes('按原答案重新获取评价'),
  })

  await setScenario(page, 'answering')
  await page.evaluate(() => {
    const button = [...document.querySelectorAll('button')].find((el) =>
      el.textContent?.includes('按原答案重新获取评价'),
    )
    button?.click()
  })
  await page.waitForFunction(() => document.body.innerText.includes('本题反馈') || document.body.innerText.includes('阶段性评价'))
  await capture(page, 'session-evaluation-1440.png', {
    width: 1440,
    height: 900,
    waitFor: () => document.body.innerText.includes('阶段性评价') || document.body.innerText.includes('正确'),
  })

  await page.goto(`${BASE}/interviews`, { waitUntil: 'load' })
  await capture(page, 'history-1440.png', { width: 1440, height: 900 })

  await page.goto(`${BASE}/interviews/41`, { waitUntil: 'load' })
  await capture(page, 'detail-1440.png', { width: 1440, height: 900 })

  await page.goto(`${BASE}/profile`, { waitUntil: 'load' })
  await capture(page, 'profile-1440.png', { width: 1440, height: 900 })

  // skip link
  await page.goto(`${BASE}/`, { waitUntil: 'load' })
  await page.focus('body')
  await page.keyboard.press('Tab')
  await capture(page, 'skip-link-focus-1440.png', { width: 1440, height: 900 })

  // 1024
  await setScenario(page, 'answering')
  await page.goto(`${BASE}/interview`, { waitUntil: 'load' })
  await capture(page, 'session-1024.png', { width: 1024, height: 768 })
  await page.goto(`${BASE}/interviews/41/report`, { waitUntil: 'load' })
  await capture(page, 'report-1024.png', { width: 1024, height: 768 })

  // --- 390 user surfaces ---
  await setViewport(page, 390, 844, true)
  await logout(page)
  await clearSite(page)
  await page.goto(`${BASE}/login`, { waitUntil: 'load' })
  await capture(page, 'login-390.png', { width: 390, height: 844, mobile: true })
  await page.goto(`${BASE}/register`, { waitUntil: 'load' })
  await capture(page, 'register-390.png', { width: 390, height: 844, mobile: true })

  await login(page, USER)
  await setScenario(page, 'answering')
  await page.goto(`${BASE}/`, { waitUntil: 'load' })
  await capture(page, 'dashboard-390.png', { width: 390, height: 844, mobile: true })

  await setScenario(page, 'no-active')
  await page.goto(`${BASE}/interviews/new`, { waitUntil: 'load' })
  await capture(page, 'create-390.png', { width: 390, height: 844, mobile: true })

  await setScenario(page, 'answering')
  await page.evaluate(() => {
    localStorage.removeItem('ai-interview.submit-records.v1')
    localStorage.removeItem('ai-interview.evaluation-gates.v1')
  }).catch(() => {})
  // durable keys are namespaced; clear all interview keys
  await page.evaluate(() => {
    Object.keys(localStorage)
      .filter((key) => key.includes('submit') || key.includes('evaluation') || key.includes('gate'))
      .forEach((key) => localStorage.removeItem(key))
  })
  await page.goto(`${BASE}/interview`, { waitUntil: 'load' })
  await page.waitForSelector('.question-content')
  await capture(page, 'session-answering-390.png', { width: 390, height: 844, mobile: true })

  await setScenario(page, 'answering')
  await submitAnswer(page)
  await page.waitForFunction(() => document.body.innerText.includes('阶段性评价') || document.body.innerText.includes('本题反馈'))
  await capture(page, 'session-evaluation-390.png', { width: 390, height: 844, mobile: true })

  await page.evaluate(() => {
    const button = [...document.querySelectorAll('button')].find((el) => el.textContent?.includes('继续追问'))
    button?.click()
  })
  await page.waitForSelector('#interview-answer:not([readonly])')
  await setScenario(page, 'blocked')
  await submitAnswer(page)
  await page.waitForFunction(() => document.body.innerText.includes('不要继续创建新的提交') || document.body.innerText.includes('本机恢复信息'))
  await capture(page, 'session-recovery-blocked-390.png', { width: 390, height: 844, mobile: true })

  await page.goto(`${BASE}/profile`, { waitUntil: 'load' })
  await capture(page, 'profile-390.png', { width: 390, height: 844, mobile: true })

  // --- admin ---
  await setViewport(page, 1440, 900, false)
  await logout(page)
  await clearSite(page)
  await setScenario(page, 'admin')
  await login(page, ADMIN)

  await page.goto(`${BASE}/admin/questions`, { waitUntil: 'load' })
  await capture(page, 'admin-questions-1440.png', { width: 1440, height: 900 })

  await page.goto(`${BASE}/admin/questions/new`, { waitUntil: 'load' })
  await capture(page, 'admin-question-form-1024.png', { width: 1024, height: 768 })

  await page.goto(`${BASE}/admin/knowledge`, { waitUntil: 'load' })
  await capture(page, 'admin-knowledge-1440.png', { width: 1440, height: 900 })

  await setViewport(page, 390, 844, true)
  await page.goto(`${BASE}/admin/questions`, { waitUntil: 'load' })
  await capture(page, 'admin-question-list-390.png', { width: 390, height: 844, mobile: true })

  await page.goto(`${BASE}/admin/questions/new`, { waitUntil: 'load' })
  await capture(page, 'admin-question-form-390.png', { width: 390, height: 844, mobile: true })

  await page.goto(`${BASE}/admin/knowledge`, { waitUntil: 'load' })
  await capture(page, 'admin-knowledge-390.png', { width: 390, height: 844, mobile: true })

  fs.writeFileSync(LOG_PATH, `${JSON.stringify(log, null, 2)}\n`)
  process.stdout.write(`viewport log ${LOG_PATH}\n`)
} finally {
  await browser.close()
}
