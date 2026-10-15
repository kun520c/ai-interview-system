import http from 'node:http'

const PORT = Number(process.env.PHASE_8B2_MOCK_PORT ?? 8080)

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

const followUpSession = {
  ...activeSession,
  currentQuestion: {
    interviewQuestionId: 89,
    category: 'SPRING',
    knowledgePoint: 'AOP',
    questionType: 'FOLLOW_UP',
    content: '结合刚才的回答，AOP 在这里怎么落地？',
  },
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
let scenario = 'no-active-recent'
let createPosted = false

function currentPayload() {
  if (scenario === 'active-recent' || scenario === 'active-empty' || scenario === 'history-error') {
    return activeSession
  }
  if (scenario === 'follow-up') {
    return followUpSession
  }
  if (scenario === 'create-500-active') {
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

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://127.0.0.1:${PORT}`)
  const path = url.pathname
  const method = req.method ?? 'GET'

  if (path === '/api/__test__/scenario' && method === 'POST') {
    const body = await readBody(req)
    if (typeof body.scenario === 'string') {
      scenario = body.scenario
      createPosted = false
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
    send(res, 200, envelope(currentPayload()))
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

  send(res, 404, envelope(null, '请求的资源不存在', 404))
})

server.listen(PORT, '127.0.0.1', () => {
  process.stdout.write(`phase-8b2 mock API listening on 127.0.0.1:${PORT} scenario=${scenario}\n`)
})
