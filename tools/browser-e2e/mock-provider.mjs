import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const runId = process.env.BROWSER_E2E_RUN_ID ?? '20261018-v3'
if (!/^[a-z0-9-]{1,64}$/.test(runId)) throw new Error('Invalid browser run ID')
const out = path.resolve('target/browser-e2e', runId)
fs.mkdirSync(out, { recursive: true })
let sequence = 0
const server = http.createServer(async (req, res) => {
  const chunks = []
  for await (const chunk of req) chunks.push(chunk)
  const body = JSON.parse(Buffer.concat(chunks).toString() || '{}')
  const id = ++sequence
  let result
  if (req.url.endsWith('/embeddings')) {
    result = { object: 'list', model: body.model, data: body.input.map((_, index) => ({
      object: 'embedding', index, embedding: Array(1024).fill(0.01),
    })), usage: { prompt_tokens: 1, total_tokens: 1 } }
  } else if (req.url === '/chat/completions') {
    const prompt = body.messages.map(x => x.content).join('\n')
    let content
    if (prompt.includes('面试报告总结助手')) {
      content = { summary: '【本地模拟AI】五道主问题已完成，Java 后端计算最终分数。',
        strengths: ['【本地模拟AI】集合、Spring 与 MySQL 的回答覆盖核心要点。'],
        weaknesses: ['【本地模拟AI】Java 基础与网络的边界条件仍需复习。'],
        suggestions: ['【本地模拟AI】结合示例复习核心机制及异常场景。'] }
    } else {
      const user = body.messages.at(-1).content
      const follow = user.includes('评价模式：FOLLOW_UP_ANSWER')
      const weak = /题目领域：(JAVA_BASIC|NETWORK)\b/.test(user)
      const ids = [...user.matchAll(/- scoringPointId=(\d+)/g)].map(m => Number(m[1]))
      if (!ids.length || !user.includes('browser-e2e-evidence')) throw new Error('Missing real scoring snapshot or hydrated evidence')
      const invalid = user.includes('E2E_INVALID_MODEL')
      const score = invalid ? 99 : weak ? 8 : 18
      content = { correctnessScore: score, completenessScore: score, depthScore: score,
        clarityScore: score, practiceScore: score, strengths: ['【本地模拟AI】已表达基本思路。'],
        missingPoints: ['【本地模拟AI】请补充边界情况。'], correction: '【本地模拟AI】结合实际示例说明核心机制。',
        scoringPointResults: ids.map(scoringPointId => ({ scoringPointId, covered: !weak,
          evidence: '【本地模拟AI】根据本次合成回答评估覆盖情况。' })),
        followUpRecommended: weak && !follow,
        suggestedFollowUp: weak && !follow ? '【本地模拟AI追问】请进一步解释该机制的边界条件和实际使用场景。' : null }
      if (user.includes('E2E_SLOW')) await new Promise(resolve => setTimeout(resolve, 2500))
    }
    result = { id: `local-mock-${id}`, object: 'chat.completion', created: 0, model: body.model,
      choices: [{ index: 0, finish_reason: 'stop', message: { role: 'assistant', content: JSON.stringify(content) } }],
      usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 } }
  } else {
    res.writeHead(404).end(); return
  }
  fs.appendFileSync(path.join(out, 'provider-wire.jsonl'), JSON.stringify({ id, method: req.method,
    path: req.url, request: body, response: result, provider: 'LOCAL_MOCK', paid: false }) + '\n')
  res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(result))
})
server.listen(18097, '127.0.0.1', () => console.log('LOCAL_MOCK_ONLY http://127.0.0.1:18097'))
