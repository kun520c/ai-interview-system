import http from 'node:http'

const PORT = Number(process.env.PHASE_8B5_MOCK_PORT ?? 8080)

const envelope = (data, message = 'success', code = 200) => ({
  code,
  message,
  data,
})

const userMe = {
  userId: 3,
  account: 'fixture.user',
  username: 'FixtureUser',
  email: 'fixture.user@example.com',
  role: 'USER',
  status: 'ENABLED',
  createdAt: '2026-01-01T00:00:00',
}

const adminMe = {
  userId: 9,
  account: 'fixture.admin',
  username: '管理员甲',
  email: 'fixture.admin@example.com',
  role: 'ADMIN',
  status: 'ENABLED',
  createdAt: '2026-01-01T00:00:00',
}

const questions = [
  {
    id: 7,
    category: 'SPRING',
    knowledgePoint: 'Bean 生命周期',
    difficulty: 'MEDIUM',
    questionContent: '请说明 Spring Bean 的完整生命周期，覆盖实例化、依赖注入、初始化回调与销毁阶段。',
    status: 'ENABLED',
    updatedAt: '2026-09-18T11:00:00',
  },
  {
    id: 8,
    category: 'MYSQL',
    knowledgePoint: '事务隔离级别',
    difficulty: 'HARD',
    questionContent: '请比较 READ COMMITTED 与 REPEATABLE READ 在当前读与快照读上的差异。',
    status: 'ENABLED',
    updatedAt: '2026-09-17T09:12:00',
  },
  {
    id: 9,
    category: 'JAVA_CONCURRENCY',
    knowledgePoint: 'ConcurrentHashMap',
    difficulty: 'MEDIUM',
    questionContent: 'ConcurrentHashMap 如何在保证并发安全的同时避免整表锁？',
    status: 'DISABLED',
    updatedAt: '2026-09-16T08:40:00',
  },
]

const questionDetail = {
  id: 7,
  category: 'SPRING',
  knowledgePoint: 'Bean 生命周期',
  difficulty: 'MEDIUM',
  content: '请说明 Spring Bean 的完整生命周期，覆盖实例化、依赖注入、初始化回调与销毁阶段。',
  referenceAnswer: '完整顺序应覆盖实例化、属性填充、Aware 回调、初始化与销毁。',
  status: 'ENABLED',
  createdAt: '2026-09-01T00:00:00',
  updatedAt: '2026-09-18T11:00:00',
  scoringPoints: [
    { pointType: 'CORE', content: '覆盖实例化与依赖注入', weight: 60 },
    { pointType: 'KEY', content: '提到销毁回调', weight: 40 },
  ],
}

const readyReport = {
  sessionId: 41,
  reportStatus: 'READY',
  overallScore: 82.5,
  result: 'GOOD',
  summary: '本场面试对 Spring 容器机制有基本掌握，但对失败路径说明不足。',
  strengths: ['能够区分实例化与依赖注入。'],
  weaknesses: ['销毁阶段覆盖不足。'],
  suggestions: ['补齐 Bean 销毁回调。'],
}

function statusReport(sessionId, reportStatus) {
  return {
    sessionId,
    reportStatus,
    overallScore: null,
    result: null,
    summary: null,
    strengths: null,
    weaknesses: null,
    suggestions: null,
  }
}

let scenario = 'P1'
let currentMe = userMe
let postedReport = false
let nextQuestionId = 88
let questionStatus = 'ENABLED'

function send(res, status, body) {
  const payload = JSON.stringify(body)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  })
  res.end(payload)
}

function readBody(req) {
  return new Promise((resolve) => {
    const chunks = []
    req.on('data', (chunk) => chunks.push(chunk))
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8')
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve({})
      }
    })
  })
}

function applyScenario(next) {
  scenario = next
  postedReport = false
  questionStatus = 'ENABLED'
  if (next.startsWith('Q') || next.startsWith('K')) {
    currentMe = adminMe
  } else {
    currentMe = userMe
  }
}

function questionPage(url) {
  const pageNum = Number(url.searchParams.get('pageNum') ?? 1)
  const pageSize = Number(url.searchParams.get('pageSize') ?? 10)
  const category = url.searchParams.get('category')
  const difficulty = url.searchParams.get('difficulty')
  const status = url.searchParams.get('status')
  const keyword = url.searchParams.get('keyword') ?? ''
  let records = questions.map((item) =>
    item.id === 7 ? { ...item, status: questionStatus } : item,
  )
  if (category) {
    records = records.filter((item) => item.category === category)
  }
  if (difficulty) {
    records = records.filter((item) => item.difficulty === difficulty)
  }
  if (status) {
    records = records.filter((item) => item.status === status)
  }
  if (keyword) {
    records = records.filter(
      (item) => item.questionContent.includes(keyword) || item.knowledgePoint.includes(keyword),
    )
  }
  const start = (pageNum - 1) * pageSize
  return {
    records: records.slice(start, start + pageSize),
    total: records.length,
    pageNum,
    pageSize,
    totalPages: Math.max(1, Math.ceil(records.length / pageSize)),
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://127.0.0.1:${PORT}`)
  const path = url.pathname
  const method = req.method ?? 'GET'

  if (path === '/api/__test__/scenario' && method === 'POST') {
    const body = await readBody(req)
    if (typeof body.scenario === 'string') {
      applyScenario(body.scenario)
    }
    send(res, 200, envelope({ scenario }))
    return
  }

  if (path === '/api/auth/login' && method === 'POST') {
    const body = await readBody(req)
    const isAdmin = body.account === 'fixture.admin' || currentMe.role === 'ADMIN'
    const me = isAdmin ? adminMe : userMe
    currentMe = me
    send(
      res,
      200,
      envelope({
        userId: me.userId,
        account: me.account,
        username: me.username,
        role: me.role,
        accessToken: 'fixture-access-token',
        tokenType: 'Bearer',
        expiresInSeconds: 7200,
      }),
    )
    return
  }

  if (path === '/api/users/me' && method === 'GET') {
    send(res, 200, envelope(currentMe))
    return
  }

  if (path === '/api/users/me/password' && method === 'PUT') {
    const body = await readBody(req)
    if (scenario === 'P2' || body.currentPassword === 'wrongpass') {
      send(res, 400, envelope(null, '请输入正确的现存密码', 400))
      return
    }
    if (body.currentPassword === body.newPassword) {
      send(res, 400, envelope(null, '新密码不能与原密码相同', 400))
      return
    }
    send(res, 200, envelope(null))
    return
  }

  if (path === '/api/interviews/current' && method === 'GET') {
    send(res, 200, envelope(null))
    return
  }

  if (path === '/api/interviews/history' && method === 'GET') {
    send(
      res,
      200,
      envelope({
        page: 1,
        pageSize: 10,
        total: 1,
        totalPages: 1,
        items: [
          {
            sessionId: 41,
            difficulty: 'MEDIUM',
            status: 'COMPLETED',
            reportStatus: 'READY',
            totalScore: 82.5,
            plannedQuestionCount: 7,
            completedQuestionCount: 7,
            startedAt: '2026-09-18T10:00:00',
            endedAt: '2026-09-18T11:20:00',
            createdAt: '2026-09-18T09:50:00',
          },
        ],
      }),
    )
    return
  }

  const reportMatch = path.match(/^\/api\/interviews\/(\d+)\/report$/)
  if (reportMatch) {
    const sessionId = Number(reportMatch[1])
    if (method === 'GET') {
      if (scenario === 'R9') {
        if (postedReport) {
          send(res, 500, envelope(null, '网络异常，请稍后重试', 500))
          return
        }
        send(res, 200, envelope(statusReport(sessionId, 'NOT_STARTED')))
        return
      }
      send(res, 200, envelope(sessionId === 41 ? readyReport : statusReport(sessionId, 'NOT_STARTED')))
      return
    }
    if (method === 'POST') {
      postedReport = true
      if (scenario === 'R9') {
        send(res, 502, envelope(null, '外部服务暂时不可用', 502))
        return
      }
      send(res, 200, envelope({ ...readyReport, sessionId }))
      return
    }
  }

  const detailMatch = path.match(/^\/api\/interviews\/(\d+)$/)
  if (detailMatch && method === 'GET') {
    const sessionId = Number(detailMatch[1])
    send(
      res,
      200,
      envelope({
        sessionId,
        difficulty: 'MEDIUM',
        status: 'COMPLETED',
        reportStatus: sessionId === 41 ? 'READY' : 'NOT_STARTED',
        totalScore: sessionId === 41 ? 82.5 : null,
        plannedQuestionCount: 7,
        completedQuestionCount: 7,
        startedAt: '2026-09-18T10:00:00',
        endedAt: '2026-09-18T11:20:00',
        createdAt: '2026-09-18T09:50:00',
        questions: [
          {
            interviewQuestionId: 88,
            questionType: 'MAIN',
            category: 'SPRING',
            knowledgePoint: 'Bean 生命周期',
            questionContent: '请说明 Spring Bean 的生命周期。',
            planOrder: 1,
            displayOrder: 1,
            status: 'ANSWERED',
            answer: {
              answerContent: '先实例化，再注入依赖，然后初始化。',
              answerStatus: 'EVALUATED',
              submittedAt: '2026-09-18T10:20:00',
            },
            evaluation: {
              evaluationPhase: 'INITIAL',
              correctnessScore: 16,
              completenessScore: 14,
              depthScore: 12,
              clarityScore: 15,
              practiceScore: 13,
              totalScore: 70,
              level: 'FAIR',
              strengths: ['说明了实例化与依赖注入的顺序。'],
              missingPoints: ['没有提到销毁阶段。'],
              correction: '补充 DisposableBean 与销毁回调的顺序。',
            },
          },
        ],
      }),
    )
    return
  }

  if (path === '/api/admin/questions' && method === 'GET') {
    send(res, 200, envelope(questionPage(url)))
    return
  }

  if (path === '/api/admin/questions' && method === 'POST') {
    await readBody(req)
    send(res, 200, envelope({ questionId: nextQuestionId++ }))
    return
  }

  const statusMatch = path.match(/^\/api\/admin\/questions\/(\d+)\/status$/)
  if (statusMatch && method === 'PUT') {
    const body = await readBody(req)
    questionStatus = body.status === 'DISABLED' ? 'DISABLED' : 'ENABLED'
    send(res, 200, envelope(null))
    return
  }

  const questionMatch = path.match(/^\/api\/admin\/questions\/(\d+)$/)
  if (questionMatch) {
    const questionId = Number(questionMatch[1])
    if (scenario === 'Q7' || questionId === 99) {
      send(res, 404, envelope(null, '题目不存在', 404))
      return
    }
    if (method === 'GET') {
      send(res, 200, envelope({ ...questionDetail, id: questionId, status: questionStatus }))
      return
    }
    if (method === 'PUT') {
      await readBody(req)
      send(res, 200, envelope(null))
      return
    }
  }

  if (path === '/api/admin/knowledge/documents' && method === 'POST') {
    await readBody(req)
    send(
      res,
      200,
      envelope({
        documentId: 15,
        fileName: 'spring-bean.md',
        fileType: 'MARKDOWN',
        documentVersion: 1,
        processingStatus: 'UPLOADED',
      }),
    )
    return
  }

  const processMatch = path.match(/^\/api\/admin\/knowledge\/documents\/(\d+)\/process$/)
  if (processMatch && method === 'POST') {
    if (scenario === 'K4') {
      send(res, 409, envelope(null, '文档正在处理中，请稍后重试', 409))
      return
    }
    if (scenario === 'K5') {
      send(res, 502, envelope(null, '外部服务暂时不可用', 502))
      return
    }
    send(res, 200, envelope(null))
    return
  }

  send(res, 404, envelope(null, '请求的资源不存在', 404))
})

server.listen(PORT, '127.0.0.1', () => {
  process.stdout.write(`phase-8b5 mock API listening on 127.0.0.1:${PORT} scenario=${scenario}\n`)
})
