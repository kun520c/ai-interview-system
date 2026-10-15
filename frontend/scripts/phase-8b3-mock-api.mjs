import http from 'node:http'

const PORT = Number(process.env.PHASE_8B3_MOCK_PORT ?? process.env.PHASE_8B2_MOCK_PORT ?? 8080)

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

const recentItem = {
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

const longQuestion = {
  ...mainQuestion,
  content: `${mainQuestion.content}\n\n${'请同时说明 BeanPostProcessor、InitializingBean、@PostConstruct 与 DisposableBean 的相对顺序。'.repeat(8)}`,
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
  strengths: [
    '说明了实例化先于依赖注入。',
    '提到了初始化回调的存在。',
  ],
  missingPoints: [
    '没有覆盖销毁阶段与 DisposableBean。',
    '没有说明 BeanPostProcessor 的前后处理位置。',
  ],
  correction:
    '完整顺序应覆盖实例化、属性填充、Aware 回调、BeanPostProcessor 前置、初始化、BeanPostProcessor 后置，以及容器关闭时的销毁回调。',
})

const longEvaluation = () => ({
  ...evaluation({ phase: 'FINAL', totalScore: 64, level: 'FAIR' }),
  strengths: Array.from({ length: 6 }, (_, index) => `做得好的第 ${index + 1} 点：对容器阶段有基本划分，并举出了与业务代码相关的例子。`),
  missingPoints: Array.from({ length: 6 }, (_, index) => `需要补充第 ${index + 1} 点：还缺少对失败路径、循环依赖与销毁顺序的说明，面试官会继续追问落地细节。`),
  correction: `${'参考修正应把每个阶段写成可验证的调用链，并明确哪些回调由容器保证、哪些由业务代码自己负责。'.repeat(6)}`,
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

/** @type {string} */
let scenario = 's1'
let createPosted = false
let submitted = false

function sessionForScenario() {
  if (scenario === 's1' || scenario === 's2' || scenario === 's7' || scenario === 's6') {
    return {
      ...activeSession,
      currentQuestion: scenario === 's1' ? mainQuestion : activeSession.currentQuestion,
    }
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
  if (scenario === 's5') {
    if (submitted) {
      return null
    }
    return {
      ...activeSession,
      completedQuestionCount: 6,
      currentQuestion: mainQuestion,
    }
  }
  if (scenario === 's3-long') {
    return {
      ...activeSession,
      currentQuestion: submitted ? followUpQuestion : longQuestion,
    }
  }
  if (scenario === 'active-recent' || scenario === 'active-empty' || scenario === 'history-error' || scenario === 'follow-up') {
    return scenario === 'follow-up'
      ? { ...activeSession, currentQuestion: followUpQuestion }
      : activeSession
  }
  if (scenario === 'create-500-active') {
    return createPosted ? activeSession : null
  }
  if (scenario === 'create-resume-other') {
    return createPosted ? activeSession : null
  }
  return null
}

function historyPayload() {
  if (scenario === 'no-active-empty' || scenario === 'active-empty') {
    return { page: 1, pageSize: 5, total: 0, totalPages: 0, items: [] }
  }
  return {
    page: 1,
    pageSize: 5,
    total: 2,
    totalPages: 1,
    items: [recentItem, pendingItem],
  }
}

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

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function answerSuccess(nextAction, nextQuestion, extraEvaluation) {
  return envelope({
    evaluation: extraEvaluation ?? evaluation({
      phase: nextAction === 'FINISH' ? 'FINAL' : 'INITIAL',
      totalScore: nextAction === 'FINISH' ? 82 : 70,
      level: nextAction === 'FINISH' ? 'GOOD' : 'FAIR',
    }),
    nextAction,
    sessionStatus: nextAction === 'FINISH' ? 'COMPLETED' : 'IN_PROGRESS',
    nextQuestion,
  })
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://127.0.0.1:${PORT}`)
  const path = url.pathname
  const method = req.method ?? 'GET'

  if (path === '/api/__test__/scenario' && method === 'POST') {
    const body = await readBody(req)
    if (typeof body.scenario === 'string') {
      scenario = body.scenario
      createPosted = false
      submitted = false
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

  if (path === '/api/auth/register' && method === 'POST') {
    send(res, 200, envelope(null))
    return
  }

  if (path === '/api/users/me' && method === 'GET') {
    send(res, 200, envelope(me))
    return
  }

  if (path === '/api/interviews/current' && method === 'GET') {
    if (scenario === 'current-error') {
      send(res, 500, envelope(null, '系统异常，请稍后重试', 500))
      return
    }
    if (scenario === 'create-uncertain' && createPosted) {
      send(res, 500, envelope(null, '系统异常，请稍后重试', 500))
      return
    }
    send(res, 200, envelope(sessionForScenario()))
    return
  }

  if (path === '/api/interviews/history' && method === 'GET') {
    if (scenario === 'history-error') {
      send(res, 500, envelope(null, '系统异常，请稍后重试', 500))
      return
    }
    send(res, 200, envelope(historyPayload()))
    return
  }

  if (path === '/api/interviews' && method === 'POST') {
    const body = await readBody(req)
    createPosted = true
    if (scenario === 'create-loading') {
      setTimeout(() => {
        send(res, 200, envelope({ ...createdSession, difficulty: body.difficulty ?? 'EASY' }))
      }, 60_000)
      return
    }
    if (scenario === 'create-uncertain' || scenario === 'create-500' || scenario === 'create-500-active') {
      send(res, 500, envelope(null, '系统异常，请稍后重试', 500))
      return
    }
    if (scenario === 'create-resume-other') {
      send(res, 200, envelope(activeSession))
      return
    }
    send(res, 200, envelope({ ...createdSession, difficulty: body.difficulty ?? 'EASY' }))
    return
  }

  const answerMatch = path.match(/^\/api\/interviews\/(\d+)\/answers$/)
  if (answerMatch && method === 'POST') {
    await readBody(req)
    if (scenario === 's2') {
      await delay(400)
      send(res, 409, envelope(null, '答案正在评估中，请稍后重试', 409))
      return
    }
    if (scenario === 's6') {
      send(res, 409, envelope(null, '当前问题已经使用其他requestId提交过答案', 409))
      return
    }
    if (scenario === 's7') {
      await delay(300)
      send(res, 502, envelope(null, '外部服务暂时不可用', 502))
      return
    }
    await delay(350)
    submitted = true
    if (scenario === 's3' || scenario === 's3-long') {
      send(res, 200, answerSuccess('FOLLOW_UP', followUpQuestion, scenario === 's3-long' ? longEvaluation() : undefined))
      return
    }
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

  send(res, 404, envelope(null, '请求的资源不存在', 404))
})

server.listen(PORT, '127.0.0.1', () => {
  process.stdout.write(`phase-8b3 mock API listening on 127.0.0.1:${PORT} scenario=${scenario}\n`)
})
