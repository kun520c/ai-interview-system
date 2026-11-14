"""Summarize the frozen D3-E scoring run and make private blind cards."""
from collections import Counter
from decimal import Decimal
from hashlib import sha256
import json
import os
from pathlib import Path
import secrets
import statistics

from d3e_scoring import RUN, owner_only

SUMMARY = Path('docs/rag/d3-e/SCORING_DIAGNOSTIC_20261114.json')
FIELDS = ('prompt_tokens', 'completion_tokens', 'total_tokens', 'prompt_cache_hit_tokens', 'prompt_cache_miss_tokens')
PENDING_REVIEW = {
    'binaryDisputes': {
        'E01.03': '回答提到共享字段、构造逻辑和带实现的抽象类，未明确说明可同时声明抽象方法。需确认整条评分点能否记为已覆盖。',
        'H25.03': '回答给出 POST 重试所需的稳定幂等键和同一载荷，未明确解释无保护重放会重复创建订单。需确认整条评分点能否记为已覆盖。',
    },
    'incompleteBoundaryNotes': {
        'M09.05': '回答明确目标元素只能按 Object 读取，未说明原列表可能包含非 Number 对象。',
        'E05-F.01': '追问回答写出 TCP 先建连接、UDP 直接发数据报，未用独立措辞明确 UDP 不需建立 TCP 式连接。',
        'H18.04': '回答说明挂起外层及审计独立提交，独立提交或回滚和外层回滚后仍保留可分别核对。',
    },
}


def read(path):
    return json.loads(path.read_bytes())


def write_new(path, value):
    with path.open('x', encoding='utf-8') as output:
        print(json.dumps(value, ensure_ascii=False, indent=2), file=output)


def check_fields(event):
    return all(type(event.get(field)) is int and event[field] >= 0 for field in FIELDS)


def diagnostic(case, row):
    if row['status'] != 'VALIDATED':
        return {key: row[key] for key in ('status', 'errorType', 'elapsedMs')}
    expected = set(case['expectedCoveredPointIds'])
    actual = {point['scoringPointId'] for point in row['evaluation']['suggestion']['scoringPointResults'] if point['covered']}
    core = {point['id'] for point in case['context']['scoringPoints'] if point['pointType'] == 'CORE'}
    return dict(status=row['status'], score=row['javaScore']['totalScore'], elapsedMs=row['elapsedMs'],
                coveredPoints=sorted(actual), expectedButUncovered=sorted(expected - actual),
                unexpectedlyCovered=sorted(actual - expected), expectedButUncoveredCore=sorted((expected - actual) & core),
                unexpectedlyCoveredCore=sorted((actual - expected) & core),
                suggestedFollowUp=row['evaluation']['suggestion']['followUpRecommended'],
                javaAction=row['javaDecision']['decisionAction'])


def aggregate(rows):
    good = [row for row in rows if row['status'] == 'VALIDATED']
    result = dict(count=len(rows), validated=len(good))
    if good:
        result.update(exactPointCoverageMatches=sum(not row['expectedButUncovered'] and not row['unexpectedlyCovered'] for row in good),
                      exactCoreCoverageMatches=sum(not row['expectedButUncoveredCore'] and not row['unexpectedlyCoveredCore'] for row in good),
                      missingPointFlags=sum(len(row['expectedButUncovered']) for row in good),
                      extraPointFlags=sum(len(row['unexpectedlyCovered']) for row in good),
                      missingCoreFlags=sum(len(row['expectedButUncoveredCore']) for row in good),
                      extraCoreFlags=sum(len(row['unexpectedlyCoveredCore']) for row in good),
                      followUpRecommendations=sum(row['suggestedFollowUp'] for row in good),
                      javaFollowUps=sum(row['javaAction'] == 'FOLLOW_UP' for row in good),
                      medianElapsedMs=statistics.median(row['elapsedMs'] for row in good))
    return result


def blind_case(case, variants, index):
    query_id = case['queryId']
    context = case['context']
    lines = [f'## {query_id}', '', context['mainQuestionContent'], '', f"回答：{context['mainAnswerContent']}"]
    if context['followUpAnswerContent'] is not None:
        lines += ['', f"追问：{context['followUpQuestionContent']}", '', f"追问回答：{context['followUpAnswerContent']}"]
    lines += ['', '评分点：', '', *[f"- {p['id']} ({p['pointType']}) {p['content']}"
                                   for p in context['scoringPoints']], '', '冻结的核对要点（允许等价表述）：', '',
              *[f'- {item}' for item in case['referenceChecks']], '']
    order = ['A', 'C'] if secrets.randbits(1) else ['C', 'A']
    index[query_id] = dict(zip(('X', 'Y'), order))
    for side, strategy in index[query_id].items():
        lines += [f'### 评价 {side}', '']
        row = variants.get(strategy)
        if row is None or row['status'] != 'VALIDATED':
            lines += ['该侧无可审阅的有效评价。', '']
            continue
        suggestion = row['evaluation']['suggestion']
        # Keep model wording, but withhold overall score, strategy, rank and similarity.
        for label, values in (('优点', suggestion['strengths']), ('不足', suggestion['missingPoints'])):
            lines += [label, '', *[f'- {value}' for value in values], '']
        lines += [f"纠正或建议：{suggestion['correction']}", '', '逐点判断：', '']
        for point in suggestion['scoringPointResults']:
            verdict = '已覆盖' if point['covered'] else '未覆盖'
            lines.append(f"- {point['scoringPointId']} {verdict}。{point['evidence']}")
        lines += ['', f"候选追问：{suggestion['suggestedFollowUp'] or '无'}", '']
    lines += ['人工选择（X / Y / 相当 / 皆有问题）：____', '', '已答被误判遗漏、概念错误、无关采纳或无效追问及依据：____', '']
    return lines


def main():
    os.umask(0o077)
    if not owner_only(RUN):
        raise SystemExit('Scoring run directory must be owner-only')
    journal = RUN / 'deepseek-usage.jsonl'
    raw = journal.read_bytes()
    events = [json.loads(line) for line in raw.splitlines()]
    counts = Counter(event['event'] for event in events)
    usage_by_attempt = {event['attempt']: event for event in events if event['event'] == 'usage' and check_fields(event)}
    usage = {field: sum(event[field] for event in usage_by_attempt.values()) for field in FIELDS}
    peak = (Decimal(usage['prompt_cache_hit_tokens']) * Decimal('0.04') + usage['prompt_cache_miss_tokens'] * 2
            + usage['completion_tokens'] * 8) / 1_000_000
    plan_path = RUN / 'frozen-scoring-plan.json'
    plan = read(plan_path)
    results = []
    lines = ['# D3-E 真实评分人工对照卡', '', '先独立审阅 X/Y，再打开答案索引。评估事实、CORE、遗漏、纠正与追问。总分、策略、检索顺序与相似分已隐藏。人工状态：待复核。', '']
    index = {}
    for case in plan['cases']:
        query_id = case['queryId']
        variants = {key: read(RUN / f'{query_id}-{key}.json') for key in ('A', 'C') if (RUN / f'{query_id}-{key}.json').exists()}
        lines += blind_case(case, variants, index)
        results.append({'queryId': query_id, **{key: diagnostic(case, row) for key, row in variants.items()}})
    summary = dict(version='D3E-scoring-v1', countPlanned=16, pairsPlanned=8,
                   protocol='固定回答与评分点，交错 A/C，各一次；C 为截取前两条合法证据的诊断对照。',
                   diagnosticsLimit='覆盖对比使用调用前冻结的工程判定，原统计与冻结夹具保留；所列争议与边界、文字反馈及追问均待独立人工复核。',
                   humanStatus='PENDING', pendingReview=PENDING_REVIEW, model='deepseek-flash',
                   frozenPlanSha256=sha256(plan_path.read_bytes()).hexdigest(),
                   fixtureSha256=plan['fixtureSha256'], evidenceSource='D3-BCD frozen production predictions',
                   evidenceNetworkAttempts=0, httpAttempts=counts['attempt'],
                   http200=sum(event['event'] == 'headers' and event['status'] == 200 for event in events),
                   usageCount=len(usage_by_attempt), usage=usage, peakEstimateCny=str(peak),
                   vendorBillReconciled=False, privateUsageJournalSha256=sha256(raw).hexdigest(),
                   aggregate={key: aggregate([row[key] for row in results if key in row]) for key in ('A', 'C')})
    private_diagnostics = RUN / 'SCORING_DIAGNOSTIC_WITH_PER_CASE.json'
    for path in (SUMMARY, private_diagnostics, RUN / 'SCORING_BLIND_CARDS.md', RUN / 'SCORING_BLIND_INDEX.json'):
        if path.exists():
            raise SystemExit(f'Refusing to overwrite {path.name}')
    with (RUN / 'SCORING_BLIND_CARDS.md').open('x', encoding='utf-8') as cards:
        print('\n'.join(lines), file=cards)
    write_new(RUN / 'SCORING_BLIND_INDEX.json', index)
    write_new(private_diagnostics, {**summary, 'perCase': results})
    SUMMARY.parent.mkdir(parents=True, exist_ok=True)
    write_new(SUMMARY, summary)


if __name__ == '__main__':
    main()
