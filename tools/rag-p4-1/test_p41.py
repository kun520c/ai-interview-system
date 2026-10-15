import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import p41


class P41Tests(unittest.TestCase):
    def test_frozen_plan_only_three_cases_and_no_label_leak(self):
        self.assertEqual(len(p41.verify()), 64)
        plan = p41.read(p41.PREP / 'plan.json')
        self.assertEqual([q['queryId'] for q in plan['queries']], ['P3-C01', 'P3-N02', 'P3-N06'])
        for q in plan['queries']:
            value = p41.read(q['payloadPath'])
            self.assertEqual(set(value), {'questionId', 'question', 'candidates'})
            self.assertEqual(len(value['candidates']), 2)
            for c in value['candidates']: self.assertEqual(set(c), {'candidateId', 'title', 'content'})
            old = p41.read(p41.ROOT / f"target/rag-p4/prepared/inputs/{q['originalP4QuestionId']}.json")
            self.assertEqual(value['question'], old['question'])
            self.assertEqual(value['candidates'], old['candidates'])

    def test_budget_and_provisional_review(self):
        plan = p41.read(p41.PREP / 'plan.json')
        self.assertLessEqual(float(plan['peakCostProxyCny']), .25)
        rows = p41.review()
        self.assertEqual(len(rows), 14)
        self.assertTrue(all('PROVISIONAL' in r['reviewSource'] for r in rows))
        c03 = next(r for r in rows if r['queryId'] == 'P3-C03' and r['candidateId'] == 'c2')
        self.assertEqual((c03['legacyLabel'], c03['suggestedVerdict']), (1, 'BACKGROUND_ONLY'))

    def isolated(self, fn):
        plan = p41.read(p41.PREP / 'plan.json')
        with tempfile.TemporaryDirectory(dir=p41.OFFLINE) as directory:
            root = Path(directory); prep, run = root / 'prepared', root / 'run-once'
            p41.write(prep / 'plan.json', plan)
            with patch.object(p41, 'PREP', prep), patch.object(p41, 'RUN', run), patch.object(p41, 'verify', return_value='test-seal'):
                fn(run)

    def test_old_authorization_rejected_before_any_reservation(self):
        def fn(run):
            with self.assertRaises(ValueError):
                p41.execute({'authorization': 'AUTHORIZE P4 12 DEEPSEEK ONCE', 'freezeSha256': 'test-seal', 'apiKey': 'MOCK'})
            self.assertFalse(run.exists())
        self.isolated(fn)

    def test_three_mock_calls_and_lock_prevents_repeat(self):
        def fn(run):
            calls = []
            def runner(*args):
                calls.append(1)
                return {'attempts': 1, 'gate': {'status': 'VALIDATED_MODEL_JUDGMENT', 'admittedCandidateIds': []},
                        'usage': {'prompt_tokens': 100, 'completion_tokens': 200}}
            credentials = {'authorization': p41.AUTH, 'freezeSha256': 'test-seal', 'apiKey': 'MOCK'}
            with contextlib.redirect_stdout(io.StringIO()): p41.execute(credentials, runner, origin='MOCK_OFFLINE')
            value = p41.read(run / 'results.json')
            self.assertEqual((len(calls), value['attemptsReserved'], value['retryCount']), (3, 3, 0))
            with self.assertRaises(FileExistsError): p41.execute(credentials, runner, origin='MOCK_OFFLINE')
            self.assertEqual(len(calls), 3)
        self.isolated(fn)

    def test_failure_keeps_lock_and_stops_without_retry(self):
        def fn(run):
            calls = []
            def runner(*args):
                calls.append(1)
                return {'attempts': 1, 'gate': {'status': 'SAFE_EMPTY', 'admittedCandidateIds': []}}
            with contextlib.redirect_stdout(io.StringIO()):
                p41.execute({'authorization': p41.AUTH, 'freezeSha256': 'test-seal', 'apiKey': 'MOCK'}, runner, origin='MOCK_OFFLINE')
            value = p41.read(run / 'results.json')
            self.assertEqual(len(calls), 1)
            self.assertEqual(value['P4_1_EXECUTION_RESULT'], 'FAIL_STOPPED_NO_RETRY')
            self.assertTrue((run / 'EXECUTION_RESERVED.lock').exists())
            self.assertEqual(sum(q['status'] == 'NOT_STARTED' for q in value['queries']), 2)
            self.assertTrue(value['cost']['possibleUnaccountedCharge'])
        self.isolated(fn)


if __name__ == '__main__':
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(P41Tests))
    p41.write(p41.OFFLINE / 'python-tests.json', {'tests': result.testsRun, 'failures': len(result.failures),
             'errors': len(result.errors), 'skipped': len(result.skipped), 'paidCalls': 0})
    sys.exit(0 if result.wasSuccessful() else 1)
