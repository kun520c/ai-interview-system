import http from 'node:http'

const PORT = Number(process.env.PHASE_8B4_MOCK_PORT ?? 8080)

const envelope = (data, message = 'success', code = 200) => ({
  code,
  message,
  data,
})

const me = {
  userId: 3,
  account: 'fixture.user',
  username: 'FixtureUser',
  email: 'fixture.user@example.com',
  role: 'USER',
  status: 'ENABLED',
  createdAt: '2026-01-01T00:00:00',
}

const evaluation = (phase = 'INITIAL', totalScore = 70, level = 'FAIR') => ({
  evaluationPhase: phase,
  correctnessScore: 16,
  completenessScore: 14,
  depthScore: 12,
  clarityScore: 15,
  practiceScore: 13,
  totalScore,
  level,
  strengths: ['说明了实例化先于依赖注入。', '提到了初始化回调的存在。'],
  missingPoints: ['没有覆盖销毁阶段与 DisposableBean。'],
  correction:
    '完整顺序应覆盖实例化、属性填充、Aware 回调、BeanPostProcessor 前置、初始化、BeanPostProcessor 后置，以及容器关闭时的销毁回调。',
})

const mainQuestion = {
  interviewQuestionId: 88,
  questionType: 'MAIN',
  category: 'SPRING',
  knowledgePoint: 'Bean 生命周期',
  questionContent:
    '请说明 Spring Bean 的完整生命周期，覆盖实例化、依赖注入、初始化回调与销毁阶段。结合容器实际调用顺序作答。',
  planOrder: 1,
  displayOrder: 1,
  status: 'ANSWERED',
  answer: {
    answerContent:
      'Bean 先实例化，再填充属性并完成依赖注入，然后执行初始化回调，容器关闭时再销毁。',
    answerStatus: 'EVALUATED',
    submittedAt: '2026-09-18T10:18:00',
  },
  evaluation: evaluation('INITIAL', 70, 'FAIR'),
}

const followUpQuestion = {
  interviewQuestionId: 89,
  questionType: 'FOLLOW_UP',
  category: 'SPRING',
  knowledgePoint: 'Bean 生命周期',
  questionContent: '如果初始化回调抛出异常，容器会如何处理已创建的依赖，以及销毁回调是否仍会执行？',
  planOrder: null,
  displayOrder: 2,
  status: 'ANSWERED',
  answer: {
    answerContent: '初始化失败时，已注入的依赖不会被当成可用 Bean，销毁回调通常仍会尽量执行。',
    answerStatus: 'EVALUATED',
    submittedAt: '2026-09-18T10:32:00',
  },
  evaluation: evaluation('FINAL', 78, 'FAIR'),
}

const secondMainQuestion = {
  interviewQuestionId: 101,
  questionType: 'MAIN',
  category: 'JAVA_CONCURRENCY',
  knowledgePoint: 'ConcurrentHashMap',
  questionContent: 'ConcurrentHashMap 如何在保证并发安全的同时避免整表锁？',
  planOrder: 2,
  displayOrder: 3,
  status: 'ANSWERED',
  answer: {
    answerContent: '它把表拆成桶，读写主要落在桶级同步和 CAS 上，而不是锁住整张表。',
    answerStatus: 'EVALUATED',
    submittedAt: '2026-09-18T10:48:00',
  },
  evaluation: evaluation('FINAL', 86, 'GOOD'),
}

const pendingSecretQuestion = {
  interviewQuestionId: 201,
  questionType: 'MAIN',
  category: 'MYSQL',
  knowledgePoint: '事务隔离级别',
  questionContent: 'PENDING_FUTURE_QUESTION_SECRET',
  planOrder: 3,
  displayOrder: 5,
  status: 'PENDING',
  answer: null,
  evaluation: null,
}

const readyReport = {
  sessionId: 41,
  reportStatus: 'READY',
  overallScore: 82.5,
  result: 'GOOD',
  summary:
    '本场面试说明候选人对 Spring 容器阶段有基本掌握，也能用 ConcurrentHashMap 解释分桶与 CAS，但对失败路径和销毁阶段的说明还不完整。',
  strengths: ['能够区分实例化与依赖注入。', '能用分桶而不是整表锁解释并发安全。'],
  weaknesses: ['销毁阶段覆盖不足。', '初始化失败路径讲得偏经验，缺少容器保证。'],
  suggestions: ['把 Bean 销毁回调写成可验证的调用链。', '用一次失败初始化把容器补偿行为讲清楚。'],
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

function historyItem(overrides) {
  return {
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
    ...overrides,
  }
}

const populatedItems = [
  historyItem({ sessionId: 41, reportStatus: 'READY', totalScore: 82.5 }),
  historyItem({
    sessionId: 42,
    difficulty: 'EASY',
    reportStatus: 'NOT_STARTED',
    totalScore: null,
    plannedQuestionCount: 5,
    completedQuestionCount: 5,
    endedAt: '2026-09-17T10:10:00',
  }),
  historyItem({
    sessionId: 43,
    difficulty: 'HARD',
    reportStatus: 'GENERATING',
    totalScore: null,
    plannedQuestionCount: 9,
    completedQuestionCount: 9,
    endedAt: '2026-09-16T16:40:00',
  }),
  historyItem({
    sessionId: 44,
    difficulty: 'MEDIUM',
    reportStatus: 'FAILED',
    totalScore: null,
    endedAt: '2026-09-15T14:12:00',
  }),
  historyItem({ sessionId: 45, difficulty: 'EASY', plannedQuestionCount: 5, completedQuestionCount: 5, endedAt: '2026-09-14T11:00:00' }),
  historyItem({ sessionId: 46, endedAt: '2026-09-13T11:00:00' }),
  historyItem({ sessionId: 47, endedAt: '2026-09-12T11:00:00' }),
  historyItem({ sessionId: 48, endedAt: '2026-09-11T11:00:00' }),
  historyItem({ sessionId: 49, endedAt: '2026-09-10T11:00:00' }),
  historyItem({ sessionId: 50, endedAt: '2026-09-09T11:00:00' }),
  historyItem({ sessionId: 51, endedAt: '2026-09-08T11:00:00' }),
  historyItem({ sessionId: 52, endedAt: '2026-09-07T11:00:00' }),
]

function completedDetail(sessionId, questions) {
  return {
    sessionId,
    difficulty: sessionId === 42 ? 'EASY' : 'MEDIUM',
    status: 'COMPLETED',
    reportStatus: 'NOT_STARTED',
    totalScore: sessionId === 41 ? 82.5 : null,
    plannedQuestionCount: sessionId === 42 ? 5 : 7,
    completedQuestionCount: sessionId === 42 ? 5 : 7,
    startedAt: '2026-09-18T10:00:00',
    endedAt: '2026-09-18T11:20:00',
    createdAt: '2026-09-18T09:50:00',
    questions,
  }
}

const activeSession = {
  sessionId: 21,
  difficulty: 'MEDIUM',
  status: 'IN_PROGRESS',
  plannedQuestionCount: 7,
  completedQuestionCount: 2,
  currentQuestion: {
    interviewQuestionId: 88,
    category: 'SPRING',
    knowledgePoint: 'Bean 生命周期',
    questionType: 'MAIN',
    content: '请说明 Spring Bean 的生命周期。',
  },
}

/** @type {string} */
let scenario = 'H1'
let generatingPolls = 0
let posted = false

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

function historyPayload(url) {
  if (scenario === 'H2') {
    return { page: 1, pageSize: 10, total: 0, totalPages: 0, items: [] }
  }
  const page = Number(url.searchParams.get('page') ?? 1)
  const pageSize = Number(url.searchParams.get('pageSize') ?? 10)
  const start = (page - 1) * pageSize
  const items = populatedItems.slice(start, start + pageSize)
  return {
    page,
    pageSize,
    total: populatedItems.length,
    totalPages: Math.ceil(populatedItems.length / pageSize),
    items,
  }
}

function detailFor(sessionId) {
  if (scenario === 'D3' || sessionId === 21) {
    return {
      sessionId: 21,
      difficulty: 'MEDIUM',
      status: 'IN_PROGRESS',
      reportStatus: 'NOT_STARTED',
      totalScore: null,
      plannedQuestionCount: 7,
      completedQuestionCount: 2,
      startedAt: '2026-09-18T10:00:00',
      endedAt: null,
      createdAt: '2026-09-18T09:50:00',
      questions: [mainQuestion, pendingSecretQuestion],
    }
  }
  if (scenario === 'R8' || sessionId === 99) {
    return null
  }
  if (scenario === 'D1' || sessionId === 47) {
    return completedDetail(47, [mainQuestion, secondMainQuestion])
  }
  if (sessionId === 42) {
    return {
      ...completedDetail(42, [mainQuestion, secondMainQuestion]),
      reportStatus: 'NOT_STARTED',
      totalScore: null,
    }
  }
  if (sessionId === 43) {
    return { ...completedDetail(43, [mainQuestion, followUpQuestion]), reportStatus: 'GENERATING', totalScore: null }
  }
  if (sessionId === 44) {
    return { ...completedDetail(44, [mainQuestion, followUpQuestion]), reportStatus: 'FAILED', totalScore: null }
  }
  return {
    ...completedDetail(sessionId, [mainQuestion, followUpQuestion, secondMainQuestion]),
    reportStatus: sessionId === 41 ? 'READY' : 'NOT_STARTED',
    totalScore: sessionId === 41 ? 82.5 : null,
  }
}

function reportFor(sessionId) {
  if (scenario === 'R6') {
    return posted ? { ...readyReport, sessionId } : statusReport(sessionId, 'NOT_STARTED')
  }
  if (scenario === 'R7') {
    return posted ? statusReport(sessionId, 'GENERATING') : statusReport(sessionId, 'NOT_STARTED')
  }
  if (scenario === 'R1' || sessionId === 42) {
    return statusReport(sessionId, posted ? 'READY' : 'NOT_STARTED')
  }
  if (scenario === 'R2' || sessionId === 43) {
    if (generatingPolls >= 30) {
      return { ...readyReport, sessionId }
    }
    return statusReport(sessionId, 'GENERATING')
  }
  if (scenario === 'R4' || sessionId === 44) {
    return statusReport(sessionId, 'FAILED')
  }
  if (scenario === 'R3' || sessionId === 41) {
    return { ...readyReport, sessionId }
  }
  return statusReport(sessionId, 'NOT_STARTED')
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://127.0.0.1:${PORT}`)
  const path = url.pathname
  const method = req.method ?? 'GET'

  if (path === '/api/__test__/scenario' && method === 'POST') {
    const body = await readBody(req)
    if (typeof body.scenario === 'string') {
      scenario = body.scenario
      generatingPolls = 0
      posted = false
    }
    send(res, 200, envelope({ scenario }))
    return
  }

  if (path === '/api/auth/login' && method === 'POST') {
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
    send(res, 200, envelope(me))
    return
  }

  if (path === '/api/interviews/current' && method === 'GET') {
    send(res, 200, envelope(scenario === 'D3' ? activeSession : null))
    return
  }

  if (path === '/api/interviews/history' && method === 'GET') {
    send(res, 200, envelope(historyPayload(url)))
    return
  }

  const reportMatch = path.match(/^\/api\/interviews\/(\d+)\/report$/)
  if (reportMatch) {
    const sessionId = Number(reportMatch[1])
    if (scenario === 'R8' || sessionId === 99) {
      send(res, 404, envelope(null, '面试会话不存在', 404))
      return
    }
    if (method === 'GET') {
      if (scenario === 'R2' || sessionId === 43) {
        generatingPolls += 1
      }
      send(res, 200, envelope(reportFor(sessionId)))
      return
    }
    if (method === 'POST') {
      posted = true
      if (scenario === 'R6') {
        send(res, 502, envelope(null, '外部服务暂时不可用', 502))
        return
      }
      if (scenario === 'R7') {
        posted = true
        req.socket.destroy()
        return
      }
      if (scenario === 'R5' || scenario === 'R1' || sessionId === 42) {
        send(res, 200, envelope({ ...readyReport, sessionId }))
        return
      }
      send(res, 200, envelope({ ...readyReport, sessionId }))
      return
    }
  }

  const detailMatch = path.match(/^\/api\/interviews\/(\d+)$/)
  if (detailMatch && method === 'GET') {
    const sessionId = Number(detailMatch[1])
    if (scenario === 'R8' || sessionId === 99) {
      send(res, 404, envelope(null, '面试会话不存在', 404))
      return
    }
    send(res, 200, envelope(detailFor(sessionId)))
    return
  }

  send(res, 404, envelope(null, '请求的资源不存在', 404))
})

server.listen(PORT, '127.0.0.1', () => {
  process.stdout.write(`phase-8b4 mock API listening on 127.0.0.1:${PORT} scenario=${scenario}\n`)
})
