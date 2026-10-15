"""Offline contract/quality-accounting tests. All provider responses are explicit mocks."""
import copy
import io
import json
from pathlib import Path
import shutil
import socket
import sys
import tempfile
import time
import unittest
from unittest import mock

# Needed with Python -I; load only this explicitly selected experiment directory.
sys.path.insert(0, str(Path(__file__).resolve().parent))
import p2

DATASET, LABELS = None, None


def offline_temp():
    # Explicit workspace temp path: inherited Windows sandbox TEMP can deny child writes.
    base = p2.ROOT / "target/rag-p2/offline/fixtures"
    base.mkdir(parents=True, exist_ok=True)
    temporary = tempfile.TemporaryDirectory(dir=base, prefix="mock-")
    if Path(temporary.name).resolve().parent != base.resolve():
        raise RuntimeError("Offline temporary fixture escaped its workspace directory")
    return temporary


def response(order=(0, 1, 2, 3, 4), tokens=100):
    return p2.encode({"request_id": "offline-mock-id", "output": {"results": [
        {"index": index, "relevance_score": .95 - rank * .15} for rank, index in enumerate(order)]},
        "usage": {"prompt_tokens": tokens, "total_tokens": tokens}})


def fixture(root):
    inputs = root / "target/rag-p2/inputs"
    inputs.mkdir(parents=True)
    for name in p2.FROZEN:
        shutil.copyfile(p2.ROOT / "target/rag-p2/inputs" / name, inputs / name)
    prepared = root / "target/rag-p2/prepared"
    prepared.mkdir()
    plan = p2.make_plan(DATASET)
    p2.write_json(prepared / "requests.json", plan)
    p2.write_json(prepared / "labels.json", LABELS)
    p2.write_json(prepared / "manifest.json", {
        "inputs": {name: {"sha256": sha, "originalPath": str(inputs / name)} for name, sha in p2.FROZEN.items()},
        "planSha256": p2.digest(p2.encode(plan)), "labelsSha256": p2.digest(p2.encode(LABELS))})
    return {"authorization": p2.AUTHORIZATION, "planSha256": p2.digest(p2.encode(plan)),
            "workspaceId": "offline-workspace", "apiKey": "offline-secret-key"}


class FrozenInputTests(unittest.TestCase):
    def test_exact_original_sha_and_float_bits(self):
        self.assertEqual(len(DATASET), 16)
        self.assertEqual(sum(len(q["rawTopK5"]) for q in DATASET), 80)
        self.assertEqual(DATASET[0]["rawTopK5"][0]["cosineFloat32"], 0.6486173272132874)
        self.assertEqual(DATASET[2]["rawTopK5"][0]["cosineFloat32Bits"], "3f3309ee")

    def test_human_notes_and_disputed_labels_remain_exact(self):
        original = p2.read_json(p2.ROOT / "target/rag-p2/inputs/human-annotations.json")
        for row_key, human in original["humanLabels"].items():
            self.assertEqual(LABELS[row_key]["humanOriginal"], human)
            self.assertEqual(str(LABELS[row_key]["label"]), human["label"])
        self.assertEqual(LABELS[p2.key(DATASET[1], DATASET[1]["rawTopK5"][2])]["label"], 1)

    def test_source_counts_and_coverage(self):
        self.assertEqual(sum(l["source"] == "HUMAN_ORIGINAL" for l in LABELS.values()), 10)
        self.assertEqual(sum(l["source"] == "AI_SUGGESTION" for l in LABELS.values()), 70)
        self.assertEqual(sum(q["coverageStatus"] == "NOT_COVERED" for q in DATASET), 4)

    def test_snapshot_tamper_is_rejected(self):
        with offline_temp() as folder:
            root = Path(folder)
            fixture(root)
            path = root / "target/rag-p2/inputs/results.json"
            path.write_bytes(path.read_bytes() + b" ")
            with self.assertRaisesRegex(p2.GateError, "SHA256 mismatch"):
                p2.load_dataset(root / "target/rag-p2/inputs")

    def test_request_plan_tamper_is_rejected(self):
        with offline_temp() as folder:
            root = Path(folder)
            fixture(root)
            path = root / "target/rag-p2/prepared/requests.json"
            path.write_bytes(path.read_bytes().replace(b'"top_n": 5', b'"top_n": 3'))
            with self.assertRaisesRegex(p2.GateError, "request plan changed"):
                p2.verify_prepared(root)

    def test_label_tamper_is_rejected(self):
        with offline_temp() as folder:
            root = Path(folder)
            fixture(root)
            path = root / "target/rag-p2/prepared/labels.json"
            path.write_bytes(path.read_bytes() + b" ")
            with self.assertRaisesRegex(p2.GateError, "labels changed"):
                p2.verify_prepared(root)

    def test_mismatch_between_old_html_and_original_human_is_rejected(self):
        inputs = p2.ROOT / "target/rag-p2/inputs"
        humans = p2.read_json(inputs / "human-annotations.json")
        humans["humanLabels"][next(iter(humans["humanLabels"]))]["label"] = "0"
        with self.assertRaisesRegex(p2.GateError, "human label changed"):
            p2.extract_labels(DATASET, humans, (inputs / "rag_p1_ai_review.html").read_text("utf-8"),
                              (inputs / "rag_p1_quality_review.md").read_text("utf-8"))


class RequestTests(unittest.TestCase):
    def test_question_only_all_five_full_text_no_label_leakage(self):
        plan = p2.make_plan(DATASET)
        self.assertEqual(len(plan["requests"]), 16)
        for q, r in zip(DATASET, plan["requests"], strict=True):
            self.assertEqual(r["body"]["input"]["query"], q["questionText"])
            self.assertNotEqual(r["body"]["input"]["query"], q["actualQueryText"])
            self.assertEqual(set(r["body"]), {"model", "input", "parameters"})
            self.assertEqual(set(r["body"]["input"]), {"query", "documents"})
            self.assertEqual(r["body"]["parameters"], {"top_n": 5, "instruct": p2.INSTRUCT})
            for h, document in zip(q["rawTopK5"], r["body"]["input"]["documents"], strict=True):
                self.assertEqual(document, h["knowledge"]["title"] + "\n\n" + h["knowledge"]["content"])
            self.assertNotIn("humanOriginal", p2.encode(r["body"]).decode())
            self.assertNotIn("rationale", p2.encode(r["body"]).decode())

    def test_budget_proxy_is_explicit_and_below_reference(self):
        plan = p2.make_plan(DATASET)
        self.assertGreater(plan["inputTokenByteProxy"], 132936)
        self.assertLess(float(plan["conservativeCostProxyCny"]), .50)
        self.assertIn("not provider tokenizer", plan["costCaveat"])

    def test_oversize_input_gate_without_truncation(self):
        changed = copy.deepcopy(DATASET)
        changed[0]["rawTopK5"][0]["knowledge"]["content"] = "中" * 30000
        with self.assertRaisesRegex(p2.GateError, "input size gate"):
            p2.make_plan(changed)

    def test_official_endpoint_requires_workspace_not_embedding_url(self):
        self.assertEqual(p2.endpoint("workspace-123"),
                         "https://workspace-123.cn-beijing.maas.aliyuncs.com" + p2.API_PATH)
        for invalid in (None, "", " ", "{WorkspaceId}", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "x/y", "a.b", "-x", "x-", "x\n", "x" * 64):
            with self.subTest(invalid=invalid), self.assertRaises(p2.GateError):
                p2.endpoint(invalid)


class ResponseTests(unittest.TestCase):
    def test_reordered_indices_map_exact_original_identity(self):
        mapped, usage, request_id = p2.validate_response(response((2, 0, 4, 1, 3)), DATASET[0])
        self.assertEqual([r["originalRank"] for r in mapped], [3, 1, 5, 2, 4])
        self.assertEqual([r["rerankRank"] for r in mapped], [1, 2, 3, 4, 5])
        self.assertEqual(mapped[0]["vectorId"], DATASET[0]["rawTopK5"][2]["vectorId"])
        self.assertEqual(mapped[0]["chunkId"], "530")
        self.assertEqual(usage, {"prompt_tokens": 100, "total_tokens": 100})
        self.assertEqual(request_id, "offline-mock-id")

    def test_tied_scores_keep_provider_order(self):
        raw = json.loads(response((4, 3, 2, 1, 0)))
        for item in raw["output"]["results"]:
            item["relevance_score"] = .5
        mapped, _, _ = p2.validate_response(p2.encode(raw), DATASET[0])
        self.assertEqual([r["originalRank"] for r in mapped], [5, 4, 3, 2, 1])

    def test_partial_result_cannot_be_mistaken_for_top5(self):
        raw = json.loads(response())
        raw["output"]["results"].pop()
        with self.assertRaisesRegex(p2.GateError, "ALL five"):
            p2.validate_response(p2.encode(raw), DATASET[0])

    def test_invalid_indices(self):
        for index in (None, -1, 5, 1.0, True, "0", 1):
            raw = json.loads(response())
            raw["output"]["results"][0]["index"] = index
            with self.subTest(index=index), self.assertRaises(p2.GateError):
                p2.validate_response(p2.encode(raw), DATASET[0])

    def test_invalid_scores(self):
        for score in (None, -0.1, 1.01, True, "0.8"):
            raw = json.loads(response())
            raw["output"]["results"][0]["relevance_score"] = score
            with self.subTest(score=score), self.assertRaises(p2.GateError):
                p2.validate_response(p2.encode(raw), DATASET[0])
        with self.assertRaises(p2.GateError):
            p2.validate_response(response().replace(b"0.95", b"NaN"), DATASET[0])

    def test_non_descending_scores_rejected(self):
        raw = json.loads(response())
        raw["output"]["results"][1]["relevance_score"] = .99
        with self.assertRaisesRegex(p2.GateError, "descending"):
            p2.validate_response(p2.encode(raw), DATASET[0])

    def test_usage_missing_or_invalid_never_assumed_zero(self):
        for name in ("prompt_tokens", "total_tokens"):
            for value in (None, -1, 0, True, 1.5, "100"):
                raw = json.loads(response())
                raw["usage"][name] = value
                with self.subTest(name=name, value=value), self.assertRaises(p2.GateError):
                    p2.validate_response(p2.encode(raw), DATASET[0])
        raw = json.loads(response())
        del raw["usage"]
        with self.assertRaises(p2.GateError):
            p2.validate_response(p2.encode(raw), DATASET[0])

    def test_inconsistent_usage_rejected(self):
        raw = json.loads(response())
        raw["usage"]["total_tokens"] = 99
        with self.assertRaisesRegex(p2.GateError, "Inconsistent"):
            p2.validate_response(p2.encode(raw), DATASET[0])

    def test_missing_id_error_and_malformed_json_rejected(self):
        for payload in (b"{}", b"not json", b"null", b"\xff", p2.encode({"code": "InvalidApiKey"})):
            with self.subTest(payload=payload), self.assertRaises(p2.GateError):
                p2.validate_response(payload, DATASET[0])
        raw = json.loads(response())
        raw["request_id"] = " "
        with self.assertRaises(p2.GateError):
            p2.validate_response(p2.encode(raw), DATASET[0])


class MetricTests(unittest.TestCase):
    def combined(self, name):
        row = next(m for m in p2.metrics(DATASET, LABELS, {}) if m["strategy"] == name)
        return row["views"]["COMBINED_PROVISIONAL"]

    def test_top5_reproduces_old_report(self):
        m = self.combined("A_Milvus_Top5")
        self.assertEqual([m[n] for n in ("selectedKnown", "direct2", "partial1", "irrelevant0", "usefulDeleted")],
                         [80, 15, 12, 53, 0])
        self.assertEqual((m["coveredQuestionsWithUseful"], m["outsideQuestionsAdmitted"]), (12, 4))

    def test_top3_reproduces_old_report(self):
        m = self.combined("B_Milvus_Top3")
        self.assertEqual([m[n] for n in ("selectedKnown", "direct2", "partial1", "irrelevant0", "usefulDeleted")],
                         [48, 15, 10, 23, 2])

    def test_070_reproduces_old_report_without_rounded_comparison(self):
        m = self.combined("C_COSINE_0.70")
        self.assertEqual([m[n] for n in ("selectedKnown", "direct2", "partial1", "irrelevant0", "usefulDeleted")],
                         [5, 3, 2, 0, 22])
        self.assertEqual((m["coveredQuestionsWithUseful"], m["outsideQuestionsAdmitted"]), (3, 0))

    def test_060_reproduces_historical_report(self):
        m = self.combined("D_COSINE_0.60_HISTORICAL")
        self.assertEqual([m[n] for n in ("selectedKnown", "direct2", "partial1", "irrelevant0", "usefulDeleted")],
                         [20, 12, 7, 1, 8])
        self.assertEqual((m["coveredQuestionsWithUseful"], m["outsideQuestionsAdmitted"]), (12, 0))

    def test_float32_point7_is_below_java_double_threshold(self):
        q = copy.deepcopy(DATASET[0])
        q["rawTopK5"][0]["cosineFloat32"] = 0.699999988079071
        self.assertNotIn(q["rawTopK5"][0], p2.selected(q, "C_COSINE_0.70", {}))

    def test_human_ai_denominators_stay_separate(self):
        row = p2.metrics(DATASET, LABELS, {})[0]
        human = row["views"]["HUMAN_ONLY"]
        ai = row["views"]["AI_ONLY_PROVISIONAL"]
        self.assertEqual((human["knownCandidates"], human["coveredDenominator"], human["outsideDenominator"]), (10, 2, 0))
        self.assertEqual((ai["knownCandidates"], ai["coveredDenominator"], ai["outsideDenominator"]), (70, 10, 4))
        self.assertEqual((human["direct2"], human["partial1"], human["irrelevant0"]), (3, 2, 5))
        self.assertEqual(human["precisionUseful"], .5)

    def test_real_rerank_pending_without_fabricated_metrics(self):
        rows = p2.metrics(DATASET, LABELS, {})
        self.assertEqual([r["status"] for r in rows[4:]], ["PENDING_REAL_RERANK"] * 3)
        self.assertTrue(all(not r["views"] for r in rows[4:]))

    def test_identity_rerank_has_zero_paired_improvement(self):
        reranks = {q["queryId"]: p2.validate_response(response(), q)[0] for q in DATASET}
        m = p2.ranking_comparison(DATASET, LABELS, reranks)["COMBINED_PROVISIONAL"]
        self.assertEqual(m["ndcgAt3QueryCount"], 12)
        self.assertEqual(m["zeroRelevantExcluded"], 4)
        self.assertEqual(m["meanPairedNdcgAt3Delta"], 0)
        self.assertEqual((m["pairedWins"], m["pairedLosses"], m["pairedTies"]), (0, 0, 12))

    def test_promoted_direct_and_demoted_irrelevant_are_measured(self):
        # Redis Rank2 is reference-only; Rank3 is directly useful according to AI suggestion.
        q = DATASET[6]
        mapped = p2.validate_response(response((2, 0, 3, 4, 1)), q)[0]
        m = p2.ranking_comparison(DATASET, LABELS, {q["queryId"]: mapped})["AI_ONLY_PROVISIONAL"]
        self.assertEqual(m["directInRerankTop2"], 2)
        self.assertEqual(m["directInOriginalTop2"], 1)
        self.assertGreater(m["irrelevantDemoted"], 0)

    def test_fixed_rerank_topk_cannot_reject_outside_queries(self):
        reranks = {q["queryId"]: p2.validate_response(response((4, 3, 2, 1, 0)), q)[0] for q in DATASET}
        rows = p2.metrics(DATASET, LABELS, reranks)
        self.assertEqual([r["selectedCandidates"] for r in rows[4:]], [16, 32, 48])
        self.assertTrue(all(r["views"]["COMBINED_PROVISIONAL"]["outsideQuestionsAdmitted"] == 4 for r in rows[4:]))

    def test_ndcg_zero_positive_unknown_and_known_values(self):
        self.assertIsNone(p2.ndcg([0, 0, 0, 0, 0], 3))
        self.assertEqual(p2.ndcg([2, 1, 0, 0, 0], 3), 1)
        self.assertLess(p2.ndcg([0, 1, 2, 0, 0], 3), 1)

    def test_dynamic_admission_zero_to_three_without_label_oracle(self):
        hits = DATASET[0]["rawTopK5"]
        self.assertEqual(p2.admitted_evidence(hits, {}), [])
        self.assertEqual(p2.admitted_evidence(hits, {h["vectorId"]: False for h in hits}), [])
        self.assertEqual(len(p2.admitted_evidence(hits, {h["vectorId"]: True for h in hits})), 3)
        with self.assertRaises(p2.GateError):
            p2.admitted_evidence(hits, {hits[0]["vectorId"]: 2})

    def test_reference_only_diagnostic_does_not_remove_mechanism(self):
        self.assertTrue(p2.low_information("## Official references\n- [REF] Java spec\n[REF]: https://example.test/spec"))
        self.assertFalse(p2.low_information("HashMap keys must remain stable.\n[REF]: https://example.test/spec"))
        self.assertTrue(p2.low_information(DATASET[6]["rawTopK5"][1]["knowledge"]["content"]))


class ExecutionTests(unittest.TestCase):
    def setUp(self):
        self.temp = offline_temp()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.credentials = fixture(self.root)
        self.output = self.root / "target/rag-p2/run-once"

    def execute(self, transport):
        return p2.execute(self.root, self.credentials, transport, origin="OFFLINE_MOCK")

    def test_sixteen_successes_persist_all_80_and_correct_usage_cost(self):
        transport = mock.Mock(return_value=(200, response()))
        result = self.execute(transport)
        self.assertEqual(transport.call_count, 16)
        self.assertEqual(result["httpAttemptsReserved"], 16)
        self.assertEqual(result["P2_RERANK_RESULT"], "OFFLINE_MOCK_COMPLETE")
        self.assertEqual(sum(len(q["rerankResults"]) for q in result["queries"]), 80)
        self.assertEqual(result["cost"]["promptTokensKnown"], 1600)
        self.assertEqual(result["cost"]["estimatedGrossCostCnyKnown"], "0.0008")
        self.assertTrue(result["cost"]["billingComplete"])
        self.assertIsNone(result["cost"]["actualInvoiceCny"])
        self.assertEqual(len(list(self.output.glob('*.request.json'))), 16)
        self.assertEqual(len(list(self.output.glob('*.response.json'))), 16)
        self.assertNotIn(self.credentials["apiKey"], (self.output / "results.json").read_text("utf-8"))
        self.assertEqual(p2.verify_saved_run(self.root, DATASET, p2.make_plan(DATASET))["httpSuccesses"], 16)

    def test_saved_response_tamper_prevents_offline_reanalysis(self):
        self.execute(mock.Mock(return_value=(200, response())))
        path = self.output / "P1I-Q01.response.json"
        path.write_bytes(path.read_bytes() + b" ")
        with self.assertRaisesRegex(p2.GateError, "response changed"):
            p2.verify_saved_run(self.root, DATASET, p2.make_plan(DATASET))

    def test_saved_result_tamper_prevents_offline_reanalysis(self):
        self.execute(mock.Mock(return_value=(200, response())))
        path = self.output / "results.json"
        path.write_bytes(path.read_bytes() + b" ")
        with self.assertRaisesRegex(p2.GateError, "Saved run SHA"):
            p2.verify_saved_run(self.root, DATASET, p2.make_plan(DATASET))

    def test_repeat_rejected_before_transport(self):
        self.execute(mock.Mock(return_value=(200, response())))
        transport = mock.Mock(return_value=(200, response()))
        with self.assertRaises(FileExistsError):
            self.execute(transport)
        transport.assert_not_called()

    def test_no_authorization_rejected_without_reservation(self):
        self.credentials["authorization"] = "yes"
        transport = mock.Mock()
        with self.assertRaisesRegex(p2.GateError, "authorization"):
            self.execute(transport)
        self.assertFalse(self.output.exists())
        transport.assert_not_called()

    def test_authorization_must_bind_exact_plan(self):
        self.credentials["planSha256"] = "0" * 64
        transport = mock.Mock()
        with self.assertRaisesRegex(p2.GateError, "plan binding"):
            self.execute(transport)
        self.assertFalse(self.output.exists())
        transport.assert_not_called()

    def test_missing_workspace_rejected_before_reservation(self):
        self.credentials["workspaceId"] = ""
        transport = mock.Mock()
        with self.assertRaisesRegex(p2.GateError, "Workspace"):
            self.execute(transport)
        self.assertFalse(self.output.exists())
        transport.assert_not_called()

    def test_missing_key_rejected_before_reservation(self):
        self.credentials["apiKey"] = ""
        transport = mock.Mock()
        with self.assertRaisesRegex(p2.GateError, "API Key"):
            self.execute(transport)
        self.assertFalse(self.output.exists())
        transport.assert_not_called()

    def test_intent_is_durable_before_transport(self):
        def inspect(url, api_key, body):
            result = p2.read_json(self.output / "results.json")
            index = result["httpAttemptsReserved"] - 1
            self.assertEqual(result["queries"][index]["status"], "INTENT_RESERVED")
            self.assertTrue((self.output / f"P1I-Q{index+1:02}.request.json").exists())
            return 200, response()
        self.execute(inspect)

    def test_invalid_key_permission_rate_limit_redirect_no_retry_or_fallback(self):
        # Each separate fixture is a mock experiment; never use the real run-once directory.
        for status in (401, 403, 404, 429, 500, 302):
            with self.subTest(status=status), offline_temp() as folder:
                root = Path(folder)
                credentials = fixture(root)
                transport = mock.Mock(return_value=(status, b'{"code":"Blocked","request_id":"mock"}'))
                with self.assertRaises(p2.GateError):
                    p2.execute(root, credentials, transport, origin="OFFLINE_MOCK")
                self.assertEqual(transport.call_count, 1)
                result = p2.read_json(root / "target/rag-p2/run-once/results.json")
                self.assertEqual(result["httpAttemptsReserved"], 1)
                self.assertEqual(result["queries"][0]["status"], "FAILED")
                self.assertEqual(sum(q["status"] == "NOT_STARTED" for q in result["queries"]), 15)
                self.assertTrue(result["cost"]["unaccountedAttemptPossible"])

    def test_timeout_is_ambiguous_and_not_retried(self):
        transport = mock.Mock(side_effect=TimeoutError("possibly charged"))
        with self.assertRaises(p2.GateError):
            self.execute(transport)
        self.assertEqual(transport.call_count, 1)
        result = p2.read_json(self.output / "results.json")
        self.assertFalse(result["cost"]["billingComplete"])
        self.assertTrue(result["cost"]["unaccountedAttemptPossible"])
        self.assertEqual(result["queries"][0]["failureType"], "TimeoutError")

    def test_partial_success_then_failure_keeps_known_usage_without_claiming_full_fee(self):
        transport = mock.Mock(side_effect=[(200, response()), (403, b'{"code":"NoPermission"}')])
        with self.assertRaises(p2.GateError):
            self.execute(transport)
        result = p2.read_json(self.output / "results.json")
        self.assertEqual(transport.call_count, 2)
        self.assertEqual(result["cost"]["promptTokensKnown"], 100)
        self.assertFalse(result["cost"]["billingComplete"])
        self.assertTrue(result["cost"]["unaccountedAttemptPossible"])

    def test_missing_usage_stops_next_request(self):
        raw = json.loads(response())
        del raw["usage"]
        transport = mock.Mock(return_value=(200, p2.encode(raw)))
        with self.assertRaises(p2.GateError):
            self.execute(transport)
        self.assertEqual(transport.call_count, 1)
        self.assertTrue((self.output / "P1I-Q01.response.json").exists())

    def test_failure_body_echoing_key_is_redacted(self):
        raw = p2.encode({"code": "InvalidApiKey", "message": self.credentials["apiKey"]})
        with self.assertRaises(p2.GateError):
            self.execute(mock.Mock(return_value=(401, raw)))
        self.assertNotIn(self.credentials["apiKey"], (self.output / "P1I-Q01.response.json").read_text("utf-8"))
        self.assertTrue(p2.read_json(self.output / "results.json")["queries"][0]["responseRedacted"])

    def test_known_usage_exceeding_budget_stops_without_seventeenth_call(self):
        transport = mock.Mock(return_value=(200, response(tokens=1000001)))
        with self.assertRaises(p2.GateError):
            self.execute(transport)
        self.assertEqual(transport.call_count, 1)
        result = p2.read_json(self.output / "results.json")
        self.assertEqual(result["httpSuccesses"], 1)
        self.assertIn("budget", result["blockedReason"])

    def test_partial_mapping_stops_and_preserves_response(self):
        raw = json.loads(response())
        raw["output"]["results"].pop()
        transport = mock.Mock(return_value=(200, p2.encode(raw)))
        with self.assertRaises(p2.GateError):
            self.execute(transport)
        self.assertEqual(transport.call_count, 1)
        self.assertTrue((self.output / "P1I-Q01.response.json").exists())


class TransportAndReportTests(unittest.TestCase):
    def test_http_post_correct_headers_body_path_timeout_and_single_attempt(self):
        reply = mock.Mock(status=200)
        reply.read.return_value = response()
        connection = mock.Mock()
        connection.getresponse.return_value = reply
        body = p2.encode(p2.make_plan(DATASET)["requests"][0]["body"])
        with mock.patch.object(p2.http.client, "HTTPSConnection", return_value=connection) as factory:
            status, payload = p2.post_once(p2.endpoint("workspace-1"), "offline-key", body)
        self.assertEqual(status, 200)
        self.assertEqual(payload, response())
        self.assertEqual(factory.call_args.args[0], "workspace-1.cn-beijing.maas.aliyuncs.com")
        self.assertEqual(factory.call_args.kwargs["timeout"], 30)
        connection.request.assert_called_once_with("POST", p2.API_PATH, body=body,
                                                  headers={"Authorization": "Bearer offline-key", "Content-Type": "application/json"})
        connection.close.assert_called_once()

    def test_transport_timeout_does_not_reconnect(self):
        connection = mock.Mock()
        connection.request.side_effect = TimeoutError()
        with mock.patch.object(p2.http.client, "HTTPSConnection", return_value=connection) as factory:
            with self.assertRaises(TimeoutError):
                p2.post_once(p2.endpoint("workspace-1"), "offline-key", b"{}")
        factory.assert_called_once()
        connection.request.assert_called_once()
        connection.close.assert_called_once()

    def test_transport_rejects_embedding_endpoint_before_connection(self):
        with mock.patch.object(p2.http.client, "HTTPSConnection") as factory:
            with self.assertRaises(p2.GateError):
                p2.post_once("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings", "key", b"{}")
        factory.assert_not_called()

    def test_html_escapes_candidate_markup_and_retains_80_cards(self):
        with offline_temp() as folder:
            directory = Path(folder)
            dataset = copy.deepcopy(DATASET)
            dataset[0]["rawTopK5"][0]["knowledge"]["content"] = '<script>alert("x")</script>'
            p2.render_report(directory, dataset, LABELS, p2.make_plan(DATASET))
            page = (directory / "review.html").read_text("utf-8")
            self.assertEqual(page.count("<article "), 80)
            self.assertNotIn("<script>", page)
            self.assertIn("&lt;script&gt;", page)
            self.assertIn("PROVISIONAL", page)
            self.assertIn("AI_SUGGESTION", page)
            self.assertIn("HUMAN_ORIGINAL", page)


def main():
    global DATASET, LABELS
    DATASET, LABELS = p2.load_dataset(p2.ROOT / "target/rag-p2/inputs")
    originals = {name: p2.digest(Path(item["originalPath"]).read_bytes()) for name, item in
                 p2.read_json(p2.ROOT / "target/rag-p2/prepared/manifest.json")["inputs"].items()}
    started = time.perf_counter()
    suite = unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__])
    with mock.patch.object(socket.socket, "connect", side_effect=AssertionError("Real network forbidden")) as network:
        result = unittest.TextTestRunner(verbosity=2).run(suite)
        network_attempts = network.call_count
    current = {name: p2.digest(Path(item["originalPath"]).read_bytes()) for name, item in
               p2.read_json(p2.ROOT / "target/rag-p2/prepared/manifest.json")["inputs"].items()}
    summary = {"type": "OFFLINE_UNIT_AND_MOCK_ONLY", "tests": result.testsRun, "failures": len(result.failures),
               "errors": len(result.errors), "skipped": len(result.skipped), "elapsedSeconds": round(time.perf_counter() - started, 3),
               "realExternalRequests": 0, "networkAttempts": network_attempts, "databaseConnections": 0,
               "databaseWrites": 0, "vectorWrites": 0, "originalSourcesUnchanged": originals == current,
               "originalSha256After": current,
               "sourceSha256": {name: p2.digest((p2.ROOT / "tools/rag-p2" / name).read_bytes())
                                for name in ("p2.py", "run.ps1", "test_p2.py", "test-entry.ps1")},
               "passed": result.wasSuccessful() and network_attempts == 0 and originals == current,
               "failedTests": [test.id() for test, _ in result.failures + result.errors]}
    output = p2.ROOT / "target/rag-p2/offline"
    output.mkdir(parents=True, exist_ok=True)
    p2.write_json(output / "test-results.json", summary)
    print(json.dumps(summary, ensure_ascii=False))
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
