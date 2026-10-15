"""P3 offline tests: real saved P2 data, mocked NEW holdout responses, no network."""
import copy
import json
from pathlib import Path
import shutil
import socket
import sys
import tempfile
import time
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import p3

DATA, LABELS, RANKED, QUERIES, CONFIG, PROTOCOL = None, None, None, None, None, None


def temp():
    base = p3.ROOT / "target/rag-p3/offline/fixtures"
    base.mkdir(parents=True, exist_ok=True)
    folder = tempfile.TemporaryDirectory(prefix="python-mock-", dir=base)
    if Path(folder.name).resolve().parent != base.resolve():
        raise AssertionError("Mock fixture escaped P3 workspace")
    return folder


def fixture(root):
    tools = root / "tools/rag-p3"
    tools.mkdir(parents=True)
    for name in p3.CORE:
        shutil.copyfile(p3.ROOT / "tools/rag-p3" / name, tools / name)
    for path in QUERIES["corpusDocumentSha256"]:
        destination = root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(p3.ROOT / path, destination)
    prepared = root / "target/rag-p3/prepared"
    prepared.mkdir(parents=True)
    freeze = p3.read(p3.ROOT / "target/rag-p3/prepared/freeze.json")
    freeze["historySha256"] = {}  # Explicit MOCK fixture, no mutation of actual history.
    p3.write(prepared / "freeze.json", freeze)
    seal = p3.digest((prepared / "freeze.json").read_bytes())
    (prepared / "freeze.sha256").write_text(seal, encoding="ascii")
    return {"authorization": p3.AUTHORIZATION, "freezeSha256": seal, "workspaceId": "mock-workspace",
            "apiKey": "mock-rerank-secret", "embeddingKey": "mock-embedding-secret", "dbPassword": "mock-password"}


def retrieval(root, credentials):
    output = root / "target/rag-p3/run-once/retrieval"
    output.mkdir()
    queries = []
    for q in QUERIES["queries"]:
        hits = copy.deepcopy(DATA[0]["rawTopK5"])
        for h in hits:
            h["vectorId"] = q["queryId"] + "-" + h["vectorId"]
            h["knowledge"]["vectorId"] = h["vectorId"]
        queries.append({"queryId": q["queryId"], "questionText": q["questionText"], "status": "COMPLETE",
                        "rawTopK5": hits, "retrievalElapsedMs": 10., "embedding": {"usage": {"total_tokens": 100}}})
    data = {"P3_RETRIEVAL_RESULT": "PASS", "origin": "OFFLINE_MOCK", "unchanged191Rows": True, "unchanged52Vectors": True,
            "calls": {"embedding": 12, "milvusSearch": 12, "deepseek": 0, "jev": 0, "mysqlWrites": 0, "milvusWrites": 0},
            "queries": queries}
    p3.write(output / "retrieval.json", data)
    return data


def response(scores=(.9, .7, .3, .2, .1)):
    return p3.encode({"request_id": "offline-mock-request", "output": {"results": [
        {"index": i, "relevance_score": score} for i, score in enumerate(scores)]},
        "usage": {"prompt_tokens": 100, "total_tokens": 100}})


class AdmissionTests(unittest.TestCase):
    def metrics(self):
        return p3.quality_metrics(DATA, LABELS, RANKED, CONFIG)

    def test_a_original_top2(self):
        m = self.metrics()[0]["views"]["COMBINED_PROVISIONAL"]
        self.assertEqual((m["selectedKnown"], m["direct2"], m["partial1"], m["irrelevant0"]), (32, 14, 7, 11))

    def test_b_rerank_top2(self):
        m = self.metrics()[1]["views"]["COMBINED_PROVISIONAL"]
        self.assertEqual((m["selectedKnown"], m["direct2"], m["partial1"], m["irrelevant0"]), (32, 15, 8, 9))

    def test_c_score_gate_23_useful_zero_irrelevant(self):
        metric = self.metrics()[2]
        m = metric["views"]["COMBINED_PROVISIONAL"]
        self.assertEqual((m["selectedKnown"], m["direct2"], m["partial1"], m["irrelevant0"]), (23, 15, 8, 0))
        self.assertEqual((m["coveredFalseRejects"], m["coveredUsefulQueries"], m["outsideCorrectRejects"]), (0, 12, 4))
        self.assertEqual(metric["zeroOneTwoDistribution"], {"0": 4, "1": 1, "2": 11})
        self.assertEqual(m["usefulRetentionRelativeToFixedRerankTop2"], 1)
        self.assertAlmostEqual(m["usefulRetentionWithinTop5"], 23/27)
        self.assertEqual(m["directRetentionWithinTop5"], 1)

    def test_d_low_information_alone_does_not_claim_rejection(self):
        metric = self.metrics()[3]
        self.assertEqual(metric["selectedTotal"], 32)
        self.assertEqual(metric["outsideZeroByPredeclaredCoverage"], 0)
        self.assertEqual(metric["views"]["COMBINED_PROVISIONAL"]["irrelevant0"], 9)

    def test_q08_second_removed_but_q02_partial_not_promoted(self):
        q8, q2 = DATA[7], DATA[1]
        chosen8 = p3.pick(q8, RANKED[q8["queryId"]], p3.STRATEGIES[2], CONFIG)
        self.assertEqual([h["rank"] for h in chosen8], [1])
        chosen2 = p3.pick(q2, RANKED[q2["queryId"]], p3.STRATEGIES[2], CONFIG)
        self.assertEqual([LABELS[p3.p2.key(q2, h)]["label"] for h in chosen2], [1, 2])
        self.assertEqual(CONFIG["evidenceSufficiency"].split(';')[0], "NOT_ASSESSED")

    def test_no_rank3_backfill(self):
        q = DATA[0]
        ranked = copy.deepcopy(RANKED[q["queryId"]])
        ranked[0]["relevanceScore"] = .2
        ranked[1]["relevanceScore"] = .1
        ranked[2]["relevanceScore"] = .99
        self.assertEqual(p3.pick(q, ranked, p3.STRATEGIES[2], CONFIG), [])

    def test_boundary_equal_half_inclusive(self):
        q = DATA[0]
        ranked = copy.deepcopy(RANKED[q["queryId"]])
        ranked[0]["relevanceScore"] = .5
        ranked[1]["relevanceScore"] = .4999999
        self.assertEqual(len(p3.pick(q, ranked, p3.STRATEGIES[2], CONFIG)), 1)

    def test_coverage_and_label_are_not_admission_oracles(self):
        q = copy.deepcopy(DATA[0])
        original = p3.pick(q, RANKED[q["queryId"]], p3.STRATEGIES[2], CONFIG)
        q["expectedCoverage"] = "NOT_COVERED"
        q["fakeHumanLabel"] = 0
        self.assertEqual(original, p3.pick(q, RANKED[q["queryId"]], p3.STRATEGIES[2], CONFIG))

    def test_human_only_does_not_evaluate_unlabelled_outside(self):
        m = self.metrics()[2]["views"]["HUMAN_ONLY"]
        self.assertEqual(m["knownPool"], 10)
        self.assertEqual(m["outsideEvaluableDenominator"], 0)
        self.assertEqual(m["coveredEvaluableDenominator"], 2)

    def test_no_labels_not_assumed_irrelevant(self):
        metric = p3.quality_metrics(DATA, {}, RANKED, CONFIG)[2]["views"]["HUMAN_ONLY"]
        self.assertEqual(metric["knownPool"], 0)
        self.assertIsNone(metric["irrelevantAdmissionRate"])
        self.assertIsNone(metric["usefulRetentionWithinTop5"])


class FreezeTests(unittest.TestCase):
    def test_12_new_exact_question_hashes_and_6_6(self):
        old = {q["questionText"] for q in DATA}
        self.assertEqual(len(QUERIES["queries"]), 12)
        self.assertEqual(sum(q["expectedCoverage"] == "COVERED" for q in QUERIES["queries"]), 6)
        for q in QUERIES["queries"]:
            self.assertNotIn(q["questionText"], old)
            self.assertEqual(p3.digest(q["questionText"].encode()), q["questionSha256"])

    def test_audit_quotes_match_frozen_documents(self):
        audit = p3.read(p3.ROOT / "tools/rag-p3/coverage-review-v1.json")
        for review in audit["reviews"]:
            self.assertFalse(review["humanConfirmed"])
            self.assertTrue(review["mechanismBoundaryReason"])
            for basis in review["basisOrClosestNonAnswer"]:
                lines = (p3.ROOT / basis["path"]).read_text("utf-8").splitlines()
                self.assertEqual(lines[basis["line"]-1], basis["quote"])
            if review["expectedCoverage"] == "NOT_COVERED":
                self.assertEqual(len(review["all21DocumentTermAudit"]), 21)

    def test_modified_rule_blocked_without_retuning(self):
        with temp() as folder:
            root = Path(folder); fixture(root)
            path = root / "tools/rag-p3/admission-v1.json"
            path.write_bytes(path.read_bytes().replace(b'"minimumRelevanceScore": 0.5', b'"minimumRelevanceScore": 0.4'))
            with self.assertRaisesRegex(p3.p2.GateError, "core changed"):
                p3.verify(root)

    def test_modified_question_blocked(self):
        with temp() as folder:
            root = Path(folder); fixture(root)
            path = root / "tools/rag-p3/queries-v1.json"
            path.write_bytes(path.read_bytes() + b" ")
            with self.assertRaises(p3.p2.GateError):
                p3.verify(root)

    def test_freeze_seal_tamper_blocked(self):
        with temp() as folder:
            root = Path(folder); fixture(root)
            path = root / "target/rag-p3/prepared/freeze.json"
            path.write_bytes(path.read_bytes() + b" ")
            with self.assertRaises(p3.p2.GateError):
                p3.verify(root)

    def test_budget_under_reference_no_actual_token_claim(self):
        budget = p3.budget_plan(QUERIES, PROTOCOL)
        self.assertLess(float(budget["conservativeCombinedCostProxyCny"]), .5)
        self.assertEqual(budget["maxCalls"], {"embedding":12,"milvusSearch":12,"rerank":12,"deepseek":0,"jev":0})
        self.assertIn("not actual tokens", budget["referenceCaveat"])


class ExecutionTests(unittest.TestCase):
    def setUp(self):
        self.temporary = temp(); self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.credentials = fixture(self.root)
        self.directory = self.root / "target/rag-p3/run-once"

    def execute(self, transport, runner=retrieval):
        return p3.execute(self.root, self.credentials, runner, transport, origin="OFFLINE_MOCK")

    def test_12_successes_and_60_exact_requests(self):
        transport = mock.Mock(return_value=(200,response()))
        result = self.execute(transport)
        self.assertEqual(transport.call_count, 12)
        self.assertEqual(result["P3_EXECUTION_RESULT"], "OFFLINE_MOCK_COMPLETE")
        self.assertEqual(result["P3_QUALITY_RESULT"], "PENDING_HUMAN_HOLDOUT_LABELS")
        self.assertEqual(result["cost"]["estimatedGrossCnyKnown"], "0.0012")
        self.assertTrue(result["cost"]["complete"])
        self.assertEqual(len(p3.read(self.directory/"labels-template.json")["humanLabels"]), 60)
        for call in transport.call_args_list:
            body = json.loads(call.args[2]); self.assertEqual(len(body["input"]["documents"]), 5)
            self.assertEqual(set(body["input"]), {"query","documents"})
            self.assertNotIn("expectedCoverage", str(body))
            self.assertNotIn("humanLabel", str(body))

    def test_repeat_refused_before_model_runner(self):
        self.execute(mock.Mock(return_value=(200,response())))
        runner=mock.Mock();transport=mock.Mock()
        with self.assertRaises(FileExistsError):self.execute(transport,runner)
        runner.assert_not_called();transport.assert_not_called()

    def test_authorization_and_freeze_must_match(self):
        for field in ("authorization","freezeSha256"):
            original=self.credentials[field];self.credentials[field]="wrong"
            runner=mock.Mock();transport=mock.Mock()
            with self.assertRaises(p3.p2.GateError):self.execute(transport,runner)
            runner.assert_not_called();transport.assert_not_called()
            self.assertFalse(self.directory.exists());self.credentials[field]=original

    def test_missing_workspace_or_credential_blocks_before_lock(self):
        for field in ("workspaceId","apiKey","embeddingKey","dbPassword"):
            original=self.credentials[field];self.credentials[field]=""
            with self.assertRaises(p3.p2.GateError):self.execute(mock.Mock())
            self.assertFalse(self.directory.exists());self.credentials[field]=original

    def test_retrieval_failure_never_calls_rerank(self):
        transport=mock.Mock();runner=mock.Mock(side_effect=p3.p2.GateError("retrieval stopped"))
        with self.assertRaises(p3.p2.GateError):self.execute(transport,runner)
        transport.assert_not_called();self.assertTrue((self.directory/"EXECUTION_RESERVED.lock").exists())

    def test_invalid_resource_after_gate_blocks_rerank(self):
        def runner(root,creds):
            data=retrieval(root,creds);data["unchanged52Vectors"]=False;return data
        transport=mock.Mock()
        with self.assertRaises(p3.p2.GateError):self.execute(transport,runner)
        transport.assert_not_called()

    def test_partial_hydration_blocks_rerank(self):
        def runner(root,creds):
            data=retrieval(root,creds);data["queries"][0]["rawTopK5"].pop();return data
        transport=mock.Mock()
        with self.assertRaises(p3.p2.GateError):self.execute(transport,runner)
        transport.assert_not_called()

    def test_http_rejection_one_attempt_and_no_fallback(self):
        transport=mock.Mock(return_value=(403,b'{"code":"Forbidden"}'))
        with self.assertRaises(p3.p2.GateError):self.execute(transport)
        self.assertEqual(transport.call_count,1)
        result=p3.read(self.directory/"results.json")
        self.assertEqual(result["rerankAttempts"],1)
        self.assertTrue(result["cost"]["possibleUnaccountedCharge"])

    def test_timeout_one_attempt_and_lock_preserved(self):
        transport=mock.Mock(side_effect=TimeoutError())
        with self.assertRaises(p3.p2.GateError):self.execute(transport)
        self.assertEqual(transport.call_count,1)
        self.assertTrue((self.directory/"EXECUTION_RESERVED.lock").exists())

    def test_intent_precedes_request(self):
        def inspect(url,key,body):
            saved=p3.read(self.directory/"results.json")
            self.assertEqual(saved["queries"][saved["rerankAttempts"]-1]["status"],"INTENT_RESERVED")
            return 200,response()
        self.execute(inspect)

    def test_provider_key_echo_is_redacted(self):
        transport=mock.Mock(return_value=(401,p3.encode({"message":self.credentials["apiKey"]})))
        with self.assertRaises(p3.p2.GateError):self.execute(transport)
        self.assertNotIn(self.credentials["apiKey"],(self.directory/"P3-C01.rerank-response.json").read_text("utf-8"))

    def test_missing_usage_stops_next_rerank(self):
        raw=json.loads(response());del raw["usage"]
        transport=mock.Mock(return_value=(200,p3.encode(raw)))
        with self.assertRaises(p3.p2.GateError):self.execute(transport)
        self.assertEqual(transport.call_count,1)

    def test_saved_run_analysis_with_mock_human_labels(self):
        # Simulate an on-disk real-response envelope entirely within this removable MOCK fixture.
        by_text={q["questionText"]:q for q in QUERIES["queries"]}
        def model(url,key,body):
            q=by_text[json.loads(body)["input"]["query"]]
            return 200,response() if q["expectedCoverage"]=="COVERED" else response((.02,.01,.008,.004,.001))
        result=self.execute(model)
        result["origin"]="REAL";result["P3_EXECUTION_RESULT"]="PASS"
        raw=p3.read(self.directory/"retrieval/retrieval.json");raw["origin"]="REAL"
        p3.write(self.directory/"retrieval/retrieval.json",raw)
        result["retrievalSha256"]=p3.digest((self.directory/"retrieval/retrieval.json").read_bytes())
        p3.write(self.directory/"results.json",result)
        (self.directory/"results.sha256").write_text(p3.digest((self.directory/"results.json").read_bytes()),encoding="ascii")
        labels=p3.read(self.directory/"labels-template.json")
        for q,actual in zip(QUERIES["queries"],raw["queries"],strict=True):
            labels["coverageConfirmation"][q["queryId"]]=q["expectedCoverage"]
            labels["primaryEvidenceSufficiency"][q["queryId"]]="SUFFICIENT" if q["expectedCoverage"]=="COVERED" else "NONE"
            for h in actual["rawTopK5"]:
                labels["humanLabels"][p3.p2.key(actual,h)]["label"]="2" if q["expectedCoverage"]=="COVERED" and h["rank"]<=2 else "0"
        path=self.root/"mock-human-labels.json";p3.write(path,labels)
        self.assertEqual(p3.analyze_holdout(self.root,path),"PASS_ON_THIS_12_CASE_HOLDOUT_ONLY")
        response_path=self.directory/"P3-C01.rerank-response.json"
        response_path.write_bytes(response_path.read_bytes()+b" ")
        with self.assertRaisesRegex(p3.p2.GateError,"response changed"):
            p3.analyze_holdout(self.root,path)

    def test_unknown_partial_embedding_fee_stays_explicit(self):
        def runner(root,credentials):
            directory=root/"target/rag-p3/run-once/retrieval";directory.mkdir()
            p3.write(directory/"retrieval.json",{"embeddingTokensKnown":123,"calls":{"embedding":2,"milvusSearch":1}})
            raise p3.p2.GateError("second embedding failed")
        with self.assertRaises(p3.p2.GateError):self.execute(mock.Mock(),runner)
        result=p3.read(self.directory/"results.json")
        self.assertEqual(result["cost"]["embeddingTokensKnown"],123)
        self.assertTrue(result["cost"]["possibleUnaccountedCharge"])
        self.assertFalse(result["cost"]["complete"])


class FormalQualityTests(unittest.TestCase):
    def data(self):
        dataset=[];labels={};ranked={};confirm={};suff={}
        for q in QUERIES["queries"]:
            actual=dict(q,rawTopK5=copy.deepcopy(DATA[0]["rawTopK5"]));dataset.append(actual)
            for h in actual["rawTopK5"]:
                labels[p3.p2.key(actual,h)]={"label":2 if q["expectedCoverage"]=="COVERED" and h["rank"]<=2 else 0,"source":"HUMAN_ORIGINAL"}
            scores=(.9,.7,.3,.2,.1) if q["expectedCoverage"]=="COVERED" else (.02,.01,.008,.004,.001)
            ranked[q["queryId"]]=p3.p2.validate_response(response(scores),actual)[0]
            confirm[q["queryId"]]=q["expectedCoverage"];suff[q["queryId"]]="SUFFICIENT" if q["expectedCoverage"]=="COVERED" else "NONE"
        return dataset,labels,ranked,confirm,suff

    def test_formal_gate_needs_human_coverage(self):
        d,l,r,c,s=self.data();c={}
        self.assertEqual(p3.formal_quality(d,l,r,c,s,CONFIG,PROTOCOL)[0],"PENDING_HUMAN_COVERAGE")

    def test_coverage_challenge_does_not_auto_reclassify(self):
        d,l,r,c,s=self.data();c["P3-N01"]="COVERED"
        self.assertEqual(p3.formal_quality(d,l,r,c,s,CONFIG,PROTOCOL)[0],"BLOCKED_COVERAGE_CHALLENGE")

    def test_ai_suggestions_cannot_pass_formal_gate(self):
        d,l,r,c,s=self.data()
        for row in l.values():row["source"]="AI_SUGGESTION"
        self.assertEqual(p3.formal_quality(d,l,r,c,s,CONFIG,PROTOCOL)[0],"PENDING_ALL60_HUMAN_LABELS")

    def test_human_sufficiency_not_inferred_from_scores(self):
        d,l,r,c,s=self.data();s["P3-C01"]="U"
        self.assertEqual(p3.formal_quality(d,l,r,c,s,CONFIG,PROTOCOL)[0],"PENDING_HUMAN_SUFFICIENCY")

    def test_mock_good_case_narrow_scope_only(self):
        d,l,r,c,s=self.data()
        self.assertEqual(p3.formal_quality(d,l,r,c,s,CONFIG,PROTOCOL)[0],"PASS_ON_THIS_12_CASE_HOLDOUT_ONLY")

    def test_realistic_high_score_hard_negative_fails_locked_rule(self):
        d,l,r,c,s=self.data()
        ranked=p3.p2.validate_response(response((.8,.7,.4,.3,.2)),d[6])[0];r[d[6]["queryId"]]=ranked
        self.assertEqual(p3.formal_quality(d,l,r,c,s,CONFIG,PROTOCOL)[0],"FAIL_UNDER_PRELOCKED_RULE")


def main():
    global DATA,LABELS,RANKED,QUERIES,CONFIG,PROTOCOL
    DATA,LABELS,RANKED,_=p3.load_p2(p3.ROOT)
    QUERIES,CONFIG,PROTOCOL,state=p3.verify(p3.ROOT)
    start=time.perf_counter()
    with mock.patch.object(socket.socket,"connect",side_effect=AssertionError("Actual network forbidden")) as network:
        result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__]))
        attempted=network.call_count
    unchanged=p3.verify_history(p3.ROOT,state["historySha256"])
    paths=list((p3.ROOT/"tools/rag-p3").glob('*.py'))+list((p3.ROOT/"tools/rag-p3").glob('*.java'))+list((p3.ROOT/"tools/rag-p3").glob('*.ps1'))
    summary={"type":"OFFLINE_PYTHON_UNIT_MOCK", "tests":result.testsRun,"failures":len(result.failures),"errors":len(result.errors),
             "skipped":len(result.skipped),"passed":result.wasSuccessful() and attempted==0 and unchanged,"networkAttempts":attempted,
             "realExternalRequests":0,"databaseConnections":0,"databaseWrites":0,"vectorWrites":0,"historyPreserved":unchanged,
             "elapsedSeconds":round(time.perf_counter()-start,3),
             "sourceSha256":{str(p.relative_to(p3.ROOT)).replace('\\','/'):p3.digest(p.read_bytes()) for p in paths},
             "failedTests":[t.id() for t,_ in result.failures+result.errors]}
    p3.write(p3.ROOT/"target/rag-p3/offline/python-tests.json",summary)
    print(json.dumps(summary,ensure_ascii=False))
    return 0 if summary["passed"] else 1


if __name__=="__main__":sys.exit(main())
