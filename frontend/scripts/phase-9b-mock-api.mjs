import http from 'node:http'

const PORT = Number(process.env.PHASE_9B_MOCK_PORT ?? 18080)

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

const readyItem = {
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
}

const readyNullItem = {
  sessionId: 43,
  difficulty: 'HARD',
  status: 'COMPLETED',
  reportStatus: 'READY',
  totalScore: null,
  plannedQuestionCount: 9,
  completedQuestionCount: 9,
  startedAt: '2026-09-16T09:00:00',
  endedAt: '2026-09-16T10:40:00',
  createdAt: '2026-09-16T08:50:00',
}

const pendingItem = {
  sessionId: 42,
  difficulty: 'EASY',
  status: 'COMPLETED',
  reportStatus: 'NOT_STARTED',
  totalScore: null,
  plannedQuestionCount: 5,
  completedQuestionCount: 5,
  startedAt: '2026-09-17T09:00:00',
  endedAt: '2026-09-17T10:10:00',
  createdAt: '2026-09-17T08:50:00',
}

const mainQuestion = {
  interviewQuestionId: 88,
  category: 'SPRING',
  knowledgePoint: 'Bean 生命周期',
  questionType: 'MAIN',
  content:
    '请说明 Spring Bean 的完整生命周期，覆盖实例化、依赖注入、初始化回调与销毁阶段。结合容器实际调用顺序作答。',
}

const followUpQuestion = {
  interviewQuestionId: 89,
  category: 'SPRING',
  knowledgePoint: 'Bean 生命周期',
  questionType: 'FOLLOW_UP',
  content: '如果初始化回调抛出异常，容器会如何处理已创建的依赖，以及销毁回调是否仍会执行？',
}

const nextMainQuestion = {
  interviewQuestionId: 101,
  category: 'JAVA_CONCURRENCY',
  knowledgePoint: 'ConcurrentHashMap',
  questionType: 'MAIN',
  content: 'ConcurrentHashMap 如何在保证并发安全的同时避免整表锁？',
}

const evaluation = ({ phase = 'INITIAL', totalScore = 70, level = 'FAIR' } = {}) => ({
  evaluationPhase: phase,
  correctnessScore: 16,
  completenessScore: 14,
  depthScore: 12,
  clarityScore: 15,
  practiceScore: 13,
  totalScore,
  level,
  strengths: ['说明了实例化先于依赖注入。', '提到了初始化回调的存在。'],
  missingPoints: ['没有覆盖销毁阶段与 DisposableBean。', '没有说明 BeanPostProcessor 的前后处理位置。'],
  correction:
    '完整顺序应覆盖实例化、属性填充、Aware 回调、BeanPostProcessor 前置、初始化、BeanPostProcessor 后置，以及容器关闭时的销毁回调。',
})

const activeSession = {
  sessionId: 21,
  difficulty: 'MEDIUM',
  status: 'IN_PROGRESS',
  plannedQuestionCount: 7,
  completedQuestionCount: 2,
  currentQuestion: mainQuestion,
}

const createdSession = {
  sessionId: 31,
  difficulty: 'EASY',
  status: 'IN_PROGRESS',
  plannedQuestionCount: 5,
  completedQuestionCount: 0,
  currentQuestion: {
    interviewQuestionId: 201,
    category: 'JAVA_BASIC',
    knowledgePoint: 'equals 与 hashCode',
    questionType: 'MAIN',
    content: '为什么重写 equals 时通常也要重写 hashCode？',
  },
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

let scenario = 'answering'
let currentMe = userMe
let submitted = false
let nextQuestionId = 88
let questionStatus = 'ENABLED'

function applyScenario(next) {
  scenario = next
  submitted = false
  questionStatus = 'ENABLED'
  if (next === 'admin' || next.startsWith('Q') || next.startsWith('K')) {
    currentMe = adminMe
  } else {
    currentMe = userMe
  }
}

function sessionForScenario() {
  if (scenario === 'no-active' || scenario === 'ready-null' || scenario === 'admin') {
    return null
  }
  if (scenario === 's5' && submitted) {
    return null
  }
  if (scenario === 's3') {
    return {
      ...activeSession,
      currentQuestion: submitted ? followUpQuestion : mainQuestion,
    }
  }
  if (scenario === 's4') {
    return {
      ...activeSession,
      completedQuestionCount: submitted ? 3 : 2,
      currentQuestion: submitted ? nextMainQuestion : mainQuestion,
    }
  }
  return activeSession
}

function historyPayload() {
  if (scenario === 'ready-null') {
    return { page: 1, pageSize: 10, total: 1, totalPages: 1, items: [readyNullItem] }
  }
  return {
    page: 1,
    pageSize: 10,
    total: 3,
    totalPages: 1,
    items: [readyItem, pendingItem, readyNullItem],
  }
}

function send(res, status, body) {
  const payload = JSON.stringify(body)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET,POST,PUT,OPTIONS',
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

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function answerSuccess(nextAction, nextQuestion) {
  return envelope({
    evaluation: evaluation({
      phase: nextAction === 'FINISH' ? 'FINAL' : 'INITIAL',
      totalScore: nextAction === 'FINISH' ? 82 : 70,
      level: nextAction === 'FINISH' ? 'GOOD' : 'FAIR',
    }),
    nextAction,
    sessionStatus: nextAction === 'FINISH' ? 'COMPLETED' : 'IN_PROGRESS',
    nextQuestion,
  })
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

function detailFor(sessionId) {
  const isReadyNull = sessionId === 43
  return {
    sessionId,
    difficulty: isReadyNull ? 'HARD' : 'MEDIUM',
    status: 'COMPLETED',
    reportStatus: 'READY',
    totalScore: isReadyNull ? null : 82.5,
    plannedQuestionCount: isReadyNull ? 9 : 7,
    completedQuestionCount: isReadyNull ? 9 : 7,
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
        evaluation: evaluation(),
      },
    ],
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://127.0.0.1:${PORT}`)
  const path = url.pathname
  const method = req.method ?? 'GET'

  if (method === 'OPTIONS') {
    send(res, 204, envelope(null))
    return
  }

  if (path === '/api/__test__/scenario' && method === 'POST') {
    const body = await readBody(req)
    if (typeof body.scenario === 'string') {
      applyScenario(body.scenario)
    }
    send(res, 200, envelope({ scenario, role: currentMe.role }))
    return
  }

  if (path === '/api/auth/login' && method === 'POST') {
    const body = await readBody(req)
    const isAdmin = body.account === 'fixture.admin' || scenario === 'admin'
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

  if (path === '/api/auth/register' && method === 'POST') {
    send(res, 200, envelope(null))
    return
  }

  if (path === '/api/users/me' && method === 'GET') {
    send(res, 200, envelope(currentMe))
    return
  }

  if (path === '/api/users/me/password' && method === 'PUT') {
    const body = await readBody(req)
    if (body.currentPassword === 'wrongpass') {
      send(res, 400, envelope(null, '请输入正确的现存密码', 400))
      return
    }
    send(res, 200, envelope(null))
    return
  }

  if (path === '/api/interviews/current' && method === 'GET') {
    send(res, 200, envelope(sessionForScenario()))
    return
  }

  if (path === '/api/interviews/history' && method === 'GET') {
    send(res, 200, envelope(historyPayload()))
    return
  }

  if (path === '/api/interviews' && method === 'POST') {
    const body = await readBody(req)
    send(res, 200, envelope({ ...createdSession, difficulty: body.difficulty ?? 'EASY' }))
    return
  }

  const reportMatch = path.match(/^\/api\/interviews\/(\d+)\/report$/)
  if (reportMatch) {
    const sessionId = Number(reportMatch[1])
    send(res, 200, envelope({ ...readyReport, sessionId }))
    return
  }

  const answerMatch = path.match(/^\/api\/interviews\/(\d+)\/answers$/)
  if (answerMatch && method === 'POST') {
    await readBody(req)
    if (scenario === 'pending') {
      await delay(250)
      send(res, 409, envelope(null, '答案正在评估中，请稍后重试', 409))
      return
    }
    if (scenario === 'blocked') {
      await delay(200)
      send(res, 409, envelope(null, '当前问题已经使用其他requestId提交过答案', 409))
      return
    }
    await delay(280)
    submitted = true
    if (scenario === 's4') {
      send(res, 200, answerSuccess('NEXT_MAIN', nextMainQuestion))
      return
    }
    if (scenario === 's5') {
      send(res, 200, answerSuccess('FINISH', null))
      return
    }
    send(res, 200, answerSuccess('FOLLOW_UP', followUpQuestion))
    return
  }

  const detailMatch = path.match(/^\/api\/interviews\/(\d+)$/)
  if (detailMatch && method === 'GET') {
    send(res, 200, envelope(detailFor(Number(detailMatch[1]))))
    return
  }

  if (path.startsWith('/api/admin/') && currentMe.role !== 'ADMIN') {
    send(res, 403, envelope(null, '没有权限访问该资源', 403))
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
    send(res, 200, envelope(null))
    return
  }

  send(res, 404, envelope(null, '请求的资源不存在', 404))
})

server.listen(PORT, '127.0.0.1', () => {
  process.stdout.write(`phase-9b mock API listening on 127.0.0.1:${PORT} scenario=${scenario}\n`)
})
