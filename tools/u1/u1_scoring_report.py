"""Write a redacted U1 score summary and private, randomized human review material."""
from collections import Counter
from hashlib import sha256
import json
import os
from pathlib import Path
import secrets
from statistics import median
import sys

from u1_budget import CACHE_RATE, DEEPSEEK_JOURNAL, INPUT_RATE, MILLION, OUTPUT_RATE, records, require
from u1_scoring import SCORING


FIXTURES = Path('docs/verification/U1_SYNTHETIC_SCORING_FIXTURES_20261114.json')
SUMMARY = Path('docs/verification/U1_SYNTHETIC_SCORING_RESULTS_20261114.json')
ARMS = ('OLD', 'NEW')


def read(path):
    return json.loads(path.read_bytes())


def write_new(path, content):
    with path.open('x', encoding='utf-8') as stream:
        json.dump(content, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write('\n')


def judge(result, expected):
    if result['status'] != 'VALIDATED':
        return {key: result[key] for key in ('status', 'elapsedMs', 'attempt', 'errorType')}
    suggestion = result['evaluation']['suggestion']
    covered = {p['scoringPointId'] for p in suggestion['scoringPointResults'] if p['covered']}
    incomplete = set(expected['partial'])
    missing = set(expected['missing'])
    incorrect = set(expected['incorrect'])
    expected_covered = set(expected['covered'])
    return {
        'status': result['status'], 'attempt': result['attempt'], 'elapsedMs': result['elapsedMs'],
        'ratedCovered': sorted(covered), 'expectedButUncovered': sorted(expected_covered - covered),
        'partialMarkedCovered': sorted(incomplete & covered),
        'missingMarkedCovered': sorted(missing & covered), 'incorrectMarkedCovered': sorted(incorrect & covered),
        'feedbackItemCounts': {key: len(suggestion[key]) for key in ('strengths', 'missingPoints')},
        'recommendedFollowUp': suggestion['followUpRecommended'], 'javaAction': result['javaDecision']['decisionAction'],
    }


def aggregate(rows):
    successful = [row for row in rows if row['status'] == 'VALIDATED']
    flags = ('expectedButUncovered', 'partialMarkedCovered', 'missingMarkedCovered', 'incorrectMarkedCovered')
    return {
        'observed': len(rows),
        'validated': len(successful),
        'flagCounts': {key: sum(len(row[key]) for row in successful) for key in flags},
        'elapsedMs': {'median': median([row['elapsedMs'] for row in successful]),
                      'max': max(row['elapsedMs'] for row in successful)} if successful else None,
        'meanModelMissingPointEntries': sum(row['feedbackItemCounts']['missingPoints'] for row in successful) / len(successful)
            if successful else None,
        'recommendedFollowUp': sum(row['recommendedFollowUp'] for row in successful),
        'javaFollowUp': sum(row['javaAction'] == 'FOLLOW_UP' for row in successful),
    }


def blind_card(lines, mapping, sample, fixture, results):
    case_id = sample['caseId']
    lines.extend([f'## {case_id}', '', f"合成回答：{fixture['answer']}", '',
                  '核对事实、已答却判为遗漏、部分覆盖表达和有没有要求题外材料。可接受等价措辞。',
                  fixture['reviewNotes'], ''])
    labels = list(ARMS)
    if secrets.randbits(1):
        labels.reverse()
    mapping[case_id] = dict(zip(('X', 'Y'), labels))
    for alias, key in mapping[case_id].items():
        output = results.get(key)
        lines.extend([f'### {alias}', ''])
        if output is None or output['status'] != 'VALIDATED':
            lines.extend(['该侧没有完成有效评分。', ''])
            continue
        suggestion = output['evaluation']['suggestion']
        # Prompt versions, scores, latency and arm labels stay hidden. Leave model prose untouched.
        lines.extend(['优点', '', *[f'- {v}' for v in suggestion['strengths']], '',
                      '缺少的内容', '', *[f'- {v}' for v in suggestion['missingPoints']], '',
                      f"纠正：{suggestion['correction']}", '', '逐点结论', ''])
        for row in suggestion['scoringPointResults']:
            lines.append(f"- {row['scoringPointId']} {'已覆盖' if row['covered'] else '未覆盖'}。{row['evidence']}")
        lines.extend(['', f"建议追问：{suggestion['suggestedFollowUp'] or '无'}", ''])
    lines.extend(['初步判断与原文依据（X / Y / 相同 / 均有问题）：____', '', ''])


def ledger():
    attempts = {}
    headers = {}
    usages = {}
    for row in records(DEEPSEEK_JOURNAL):
        n = row['attempt']
        if row['event'] == 'attempt' and row['operation'] == 'scoring-comparison':
            attempts[n] = row
        elif n in attempts and row['event'] == 'headers':
            headers[n] = row['status']
        elif n in attempts and row['event'] == 'usage':
            usages[n] = row
    usage_fields = ('prompt_tokens', 'completion_tokens', 'total_tokens', 'prompt_cache_hit_tokens', 'prompt_cache_miss_tokens')
    usable = [usage for usage in usages.values()
              if all(type(usage.get(key)) is int and usage[key] >= 0 for key in usage_fields)]
    totals = {key: sum(row[key] for row in usable) for key in usage_fields}
    for row in usable:
        require(row['prompt_cache_hit_tokens'] + row['prompt_cache_miss_tokens'] == row['prompt_tokens']
                and row['prompt_tokens'] + row['completion_tokens'] == row['total_tokens'], 'U1_DIAGNOSTIC_USAGE_TOTAL')
    measured = (totals['prompt_cache_hit_tokens'] * CACHE_RATE + totals['prompt_cache_miss_tokens'] * INPUT_RATE
                + totals['completion_tokens'] * OUTPUT_RATE) / MILLION
    return {'deepseekAttempts': len(attempts), 'embeddingAttempts': 0,
            'httpStatusCounts': dict(Counter(headers.values())), 'attemptsWithoutHeaders': sorted(attempts.keys() - headers.keys()),
            'usableUsageRecords': len(usable), 'usage': totals, 'peakMeasuredEstimateCny': str(measured),
            'journalSha256AtReport': sha256(DEEPSEEK_JOURNAL.read_bytes()).hexdigest(), 'vendorBillReconciled': False}


def main():
    os.umask(0o077)
    private_cards, private_index, private_results = (
        SCORING / name for name in ('BLIND_CARDS.md', 'BLIND_INDEX.json', 'SCORING_DETAILS.json')
    )
    require(not any(path.exists() for path in (private_cards, private_index, private_results, SUMMARY)),
            'U1_SCORE_REPORT_EXISTS')
    require((SCORING / 'started.json').exists(), 'U1_NO_SCORING_RUN')
    require(sys.argv[1:] in (['completed'], ['stopped']), 'Report mode must be completed or stopped')
    run_status = sys.argv[1]
    plan_path = SCORING / 'frozen-plan.json'
    plan = read(plan_path)
    fixtures = {fixture['caseId']: fixture for fixture in read(FIXTURES)['cases']}
    require(sha256(FIXTURES.read_bytes()).hexdigest() == plan['fixtureSha256'], 'U1_FIXTURE_SHA')
    spend = ledger()
    require(run_status == 'stopped' or spend['deepseekAttempts'] == 16, 'U1_SCORING_STILL_RUNNING')
    marker = read(SCORING / 'started.json')
    lines = ['# U1 两题评分盲审', '', '仅含固定合成答文。先独立审阅并记录，再由协调员打开索引。X/Y 顺序逐题随机。',
             '评价重点为正确机制、已答误作遗漏、部分覆盖表述、事实误判、是否越界和追问价值。', '']
    per_case = []
    mapping = {}
    for sample in plan['samples']:
        case_id = sample['caseId']
        results = {}
        for arm in ARMS:
            path = SCORING / f'{case_id}-{arm}.json'
            if path.exists():
                results[arm] = read(path)
        blind_card(lines, mapping, sample, fixtures[case_id], results)
        per_case.append({'caseId': case_id,
                         **{arm: judge(result, sample['expectations']) for arm, result in results.items()}})
    covered = sum(arm in item for item in per_case for arm in ARMS)
    require(run_status == 'stopped' or covered == 16, 'U1_INCOMPLETE_SCORING')
    with private_cards.open('x', encoding='utf-8') as fp:
        fp.write('\n'.join(lines))
    write_new(private_index, mapping)
    # The result itself contains no private answer text, vendor response, trace, blind alias or account value.
    overview = {arm: aggregate([row[arm] for row in per_case if arm in row]) for arm in ARMS}
    validated_pairs = [row for row in per_case if all(row.get(arm, {}).get('status') == 'VALIDATED' for arm in ARMS)]
    paired_overview = {arm: aggregate([row[arm] for row in validated_pairs]) for arm in ARMS}
    summary = {'version': 'U1-synthetic-scoring-results-v1-20261114', 'status': run_status, 'model': marker['model'],
               'pairedSyntheticCasesPlanned': 8, 'responsesObserved': covered,
               'pairedCasesObserved': sum(all(arm in row for arm in ARMS) for row in per_case),
               'pairedCasesBothValidated': len(validated_pairs), 'matchedValidatedGroups': paired_overview,
               'fixedRagEvidence': 'D3 historical v3, all 5 items were serialized identically in both v3 and v4.',
               'fixtureSha256': plan['fixtureSha256'], 'contentSha256': plan['contentSha256'],
               'frozenPlanSha256': marker['planSha256'],
               'promptSourceSha256': {'OLD': plan['legacyPromptSourceSha256'], 'NEW': plan['candidatePromptSourceSha256']},
               'scoringBoundary': 'Two arms compare question content and Prompt as a single bundle at fixed evidence. No independent effect size, significance, or revised retrieval claim.',
               'partialBoundary': 'Partial point flags require manual review. The report counts Boolean coverage against frozen engineering notes, not human ground truth.',
               'humanReview': 'PENDING', 'spend': spend, 'groups': overview,
               'perCaseDetails': 'Owner-only scoring/SCORING_DETAILS.json. Open after the human review is locked.'}
    write_new(private_results, {**summary, 'cases': per_case})
    write_new(SUMMARY, summary)
    print(f'Recorded {covered}/16 model observations and separate private blind materials.')


if __name__ == '__main__':
    main()
