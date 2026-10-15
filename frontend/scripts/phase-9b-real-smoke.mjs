import http from 'node:http'

const HOST = '127.0.0.1'
const PORT = 8080

function request(method, path, { token, body } = {}) {
  return new Promise((resolve, reject) => {
    const payload = body === undefined ? null : JSON.stringify(body)
    const headers = { Accept: 'application/json' }
    if (payload) {
      headers['Content-Type'] = 'application/json'
      headers['Content-Length'] = Buffer.byteLength(payload)
    }
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
    const req = http.request({ host: HOST, port: PORT, path, method, headers }, (res) => {
      const chunks = []
      res.on('data', (chunk) => chunks.push(chunk))
      res.on('end', () => {
        const raw = Buffer.concat(chunks).toString('utf8')
      let json
      try {
        json = raw ? JSON.parse(raw) : null
      } catch {
        json = { raw }
      }
        resolve({ status: res.statusCode, json })
      })
    })
    req.on('error', reject)
    if (payload) {
      req.write(payload)
    }
    req.end()
  })
}

const stamp = Date.now()
const account = `p9b_${stamp}`
const report = []

function record(name, ok, detail) {
  report.push({ name, ok, detail })
  process.stdout.write(`${ok ? 'PASS' : 'FAIL'} ${name} ${detail}\n`)
}

try {
  const health = await request('POST', '/api/auth/login', {
    body: { account: 'no-such-user', password: 'placeholder1' },
  })
  record('backend-reachable', true, `login probe HTTP ${health.status}`)
} catch (error) {
  record('backend-reachable', false, error.message)
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
  process.exit(2)
}

const register = await request('POST', '/api/auth/register', {
  body: {
    account,
    username: account,
    email: `${account}@example.com`,
    password: 'fixturepass',
  },
})
record(
  'register',
  register.status === 200 && register.json?.code === 200,
  `HTTP ${register.status} code=${register.json?.code} msg=${register.json?.message}`,
)

const login = await request('POST', '/api/auth/login', {
  body: { account, password: 'fixturepass' },
})
const token = login.json?.data?.accessToken
record(
  'login',
  login.status === 200 && Boolean(token),
  `HTTP ${login.status} code=${login.json?.code} token=${Boolean(token)}`,
)

if (!token) {
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
  process.exit(1)
}

const me = await request('GET', '/api/users/me', { token })
record(
  'me',
  me.status === 200 && me.json?.data?.account === account && me.json?.data?.role === 'USER',
  `HTTP ${me.status} account=${me.json?.data?.account} role=${me.json?.data?.role}`,
)

const history = await request('GET', '/api/interviews/history?page=1&pageSize=10', { token })
record(
  'history-empty-or-owned',
  history.status === 200 && history.json?.code === 200,
  `HTTP ${history.status} total=${history.json?.data?.total} items=${history.json?.data?.items?.length}`,
)

const password = await request('PUT', '/api/users/me/password', {
  token,
  body: { currentPassword: 'fixturepass', newPassword: 'fixturepass2' },
})
record(
  'password-change',
  password.status === 200 && password.json?.code === 200,
  `HTTP ${password.status} code=${password.json?.code} msg=${password.json?.message}`,
)

const relogin = await request('POST', '/api/auth/login', {
  body: { account, password: 'fixturepass2' },
})
const token2 = relogin.json?.data?.accessToken ?? token
record('relogin-after-password', Boolean(token2) && relogin.status === 200, `HTTP ${relogin.status}`)

const admin = await request('GET', '/api/admin/questions?pageNum=1&pageSize=10', { token: token2 })
record(
  'user-admin-403',
  admin.status === 403,
  `HTTP ${admin.status} code=${admin.json?.code} msg=${admin.json?.message}`,
)

const current = await request('GET', '/api/interviews/current', { token: token2 })
record(
  'current',
  current.status === 200 && current.json?.code === 200,
  `HTTP ${current.status} session=${JSON.stringify(current.json?.data)}`,
)

const created = await request('POST', '/api/interviews', {
  token: token2,
  body: { difficulty: 'EASY' },
})
record(
  'create-interview',
  created.status === 200 && created.json?.code === 200,
  `HTTP ${created.status} code=${created.json?.code} msg=${created.json?.message} sessionId=${created.json?.data?.sessionId}`,
)

const failed = report.filter((item) => !item.ok)
process.stdout.write(`SMOKE_FAILED=${failed.length}\n`)
process.exit(failed.length ? 1 : 0)
