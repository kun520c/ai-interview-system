import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import p4


class OfflineTests(unittest.TestCase):
    def test_history_and_input_seal(self):
        self.assertEqual(len(p4.verify()), 64)

    def test_no_evaluation_labels_online(self):
        for p in (p4.PREP / 'inputs').glob('*.json'):
            row = p4.read(p)
            self.assertEqual(set(row), {'questionId', 'question', 'candidates'})
            self.assertTrue(row['questionId'].startswith('g'))
            for c in row['candidates']:
                self.assertEqual(set(c), {'candidateId', 'title', 'content'})

    def test_canonical_data_is_exact(self):
        for i, q in enumerate(p4.dataset(), 1):
            self.assertEqual(p4.read(p4.PREP / f'inputs/g{i:02d}.json'), p4.online_input(q, i))

    def test_corrected_label_provenance(self):
        candidates = [c for q in p4.dataset() for c in q['candidates']]
        self.assertEqual(sum(c['labelSource'] == 'USER_AUTHORIZED_AI_CORRECTION' for c in candidates), 9)
        self.assertEqual(sum(c['label'] > 0 for c in candidates), 19)

    def test_exact_baselines_and_background_not_irrelevant(self):
        d = p4.read(p4.PREP / 'baseline-comparison.json')
        a, b = d['A_RERANK_TOP2'], d['B_SCORE_050']
        self.assertEqual((a['selected'], a['direct2'], a['partial1'], a['irrelevant0']), (24, 11, 6, 7))
        self.assertEqual((b['selected'], b['direct2'], b['partial1'], b['irrelevant0']), (15, 11, 4, 0))
        self.assertEqual((b['outsideCorrectRejects'], b['outsideNonCoreAdmitted'], b['coveredFalseRejects']), (4, 3, 0))
        self.assertEqual(b['coveredUsefulRetentionRelativeToTop2'], 1)

    def test_budget_and_limits(self):
        plan = p4.read(p4.PREP / 'plan.json')
        self.assertEqual(len(plan['queries']), 12)
        self.assertLess(float(plan['peakCostProxyCny']), 1)
        self.assertEqual(plan['protocol']['retryCount'], 0)

    def test_cache_cost_and_unknown_usage(self):
        protocol = p4.read(p4.PREP / 'plan.json')['protocol']
        m = p4.usage_cost([{'status': 'COMPLETE', 'usage': {'prompt_tokens': 100, 'completion_tokens': 200,
                            'prompt_cache_hit_tokens': 20, 'prompt_cache_miss_tokens': 80}}], protocol)
        self.assertEqual(m['peakGrossEstimateCnyKnown'], '0.0017608')
        self.assertTrue(p4.usage_cost([{'status': 'SAFE_EMPTY_FAILED'}], protocol)['possibleUnaccountedCharge'])

    def isolated_execute(self, fn):
        with tempfile.TemporaryDirectory(dir=p4.OFFLINE) as folder:
            base = Path(folder); prep, run = base / 'prepared', base / 'run-once'
            plan = p4.read(p4.PREP / 'plan.json'); p4.write(prep / 'plan.json', plan)
            with patch.object(p4, 'PREP', prep), patch.object(p4, 'RUN', run), patch.object(p4, 'verify', return_value='seal'), patch.object(p4, 'analyze'):
                fn(run)

    def test_authorization_required_before_reservation(self):
        def body(run):
            with self.assertRaises(ValueError): p4.execute({'authorization': 'NO'})
            self.assertFalse(run.exists())
        self.isolated_execute(body)

    def test_stop_on_first_failure_empty_no_retry(self):
        def body(run):
            calls = []
            def runner(*args):
                calls.append(1)
                return {'attempts': 1, 'gate': {'status': 'SAFE_EMPTY', 'admittedCandidateIds': []}}
            p4.execute({'authorization': p4.AUTH, 'freezeSha256': 'seal', 'apiKey': 'mock'}, runner, origin='MOCK_OFFLINE')
            result = p4.read(run / 'results.json')
            self.assertEqual((len(calls), result['attemptsReserved']), (1, 1))
            self.assertEqual(result['queries'][0]['gate']['admittedCandidateIds'], [])
            self.assertTrue((run / 'EXECUTION_RESERVED.lock').exists())
            self.assertEqual(sum(q['status'] == 'NOT_STARTED' for q in result['queries']), 11)
            with self.assertRaises(FileExistsError): p4.execute({'authorization': p4.AUTH, 'freezeSha256': 'seal', 'apiKey': 'mock'}, runner, origin='MOCK_OFFLINE')
            self.assertEqual(len(calls), 1)
        self.isolated_execute(body)

    def test_exact_twelve_mock_calls_single_run(self):
        def body(run):
            calls = []
            def runner(*args):
                calls.append(1)
                return {'attempts': 1, 'gate': {'status': 'VALIDATED_MODEL_JUDGMENT', 'admittedCandidateIds': []},
                        'usage': {'prompt_tokens': 100, 'completion_tokens': 200}}
            p4.execute({'authorization': p4.AUTH, 'freezeSha256': 'seal', 'apiKey': 'mock'}, runner, origin='MOCK_OFFLINE')
            result = p4.read(run / 'results.json')
            self.assertEqual((len(calls), result['attemptsReserved'], result['P4_EXECUTION_RESULT']), (12, 12, 'PASS'))
            self.assertEqual(result['retryCount'], 0)
        self.isolated_execute(body)

    def test_semantic_review_required_and_no_auto_generalization(self):
        rows = p4.dataset()
        selected = {q['queryId']: [c['vectorId'] for c in q['candidates'][:2]] if q['covered'] else [] for q in rows}
        with tempfile.TemporaryDirectory(dir=p4.OFFLINE) as folder:
            run = Path(folder)
            result = {'P4_EXECUTION_RESULT': 'PASS', 'queries': [{'status': 'COMPLETE'} for _ in rows]}
            p4.write(run / 'results.json', result)
            review = {'sourceResultSha256': p4.digest((run / 'results.json').read_bytes()), 'reviewSource': 'HUMAN_PER_ITEM',
                      'reviewer': 'OFFLINE_FIXTURE_NOT_REAL_REVIEW', 'queries': [
                          {'queryId': q['queryId'], 'questionMechanismDecompositionValid': True,
                           'coreCandidates': [{'vectorId': c['vectorId'], 'humanCoreSupport': q['covered'], 'reason': 'FIXTURE_ONLY'} for c in q['candidates'][:2]]}
                          for q in rows]}
            with patch.object(p4, 'RUN', run):
                assessment = p4.reviewed_quality(rows, result, selected, review)
                self.assertEqual(assessment['P4_QUALITY_RESULT'], 'PASS_DEVELOPMENT_SET_ONLY')
                self.assertFalse(assessment['formalGeneralizationVerified'])
                # N02 background admitted despite label1: semantic quality must fail.
                selected['P3-N02'] = [rows[7]['candidates'][0]['vectorId']]
                self.assertEqual(p4.reviewed_quality(rows, result, selected, review)['P4_QUALITY_RESULT'], 'FAIL_DEVELOPMENT_SET')
                review['reviewSource'] = 'AI_SUGGESTION'
                with self.assertRaises(ValueError): p4.reviewed_quality(rows, result, selected, review)


if __name__ == '__main__':
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(OfflineTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    p4.write(p4.OFFLINE / 'python-tests.json', {'type': 'OFFLINE_PYTHON', 'tests': result.testsRun,
             'failures': len(result.failures), 'errors': len(result.errors), 'skipped': len(result.skipped), 'paidRequests': 0})
    sys.exit(0 if result.wasSuccessful() else 1)
