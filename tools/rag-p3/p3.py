"""Independent P3 experiment: read existing P2; preregister a 12-question holdout."""
import argparse
from collections import Counter
from decimal import Decimal
import html
import json
import math
import os
from pathlib import Path
import statistics
import struct
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "rag-p2"))
import p2

ROOT = Path(__file__).resolve().parents[2]
AUTHORIZATION = "AUTHORIZE P3 12 EACH ONCE"
P2_RESULT_SHA = "682cfb1c4b39a726c575bbba8832270b35bc0ee646927c0b0e45a32a85b0ff0a"
CORE = {
    "admission-v1.json": "5bd12e04cfa981523016c066c66df117fe29cabef1e5bfed553bf4b9400d498a",
    "coverage-review-v1.json": "12821ea71274c3677cc8d89a8d9da87b2c520bd39cb60947725372d9cd7d1b60",
    "protocol-v1.json": "b14d721d2a977e434a2feac5548bf844dd771cb66ee476e50eea5a23b73b9c64",
    "queries-v1.json": "c32f89f36f1e636440bc33bb4489089ba2eebe3998c4093ed0e649ac72f675e8",
}
STRATEGIES = ["A_MILVUS_TOP2", "B_RERANK_TOP2", "C_RERANK_TOP2_SCORE_GATE", "D_RERANK_TOP2_LOW_INFORMATION"]
VIEWS = ["HUMAN_ONLY", "AI_ONLY_PROVISIONAL", "COMBINED_PROVISIONAL"]
require, write, read, encode, digest = p2.require, p2.write_json, p2.read_json, p2.encode, p2.digest


def load_core(root):
    folder = root / "tools/rag-p3"
    for name, expected in CORE.items():
        require(digest((folder / name).read_bytes()) == expected, f"Preregistered core changed: {name}")
    queries, config, protocol = [read(folder / name) for name in ("queries-v1.json", "admission-v1.json", "protocol-v1.json")]
    require(len(queries["queries"]) == 12 and Counter(q["expectedCoverage"] for q in queries["queries"]) ==
            {"COVERED": 6, "NOT_COVERED": 6}, "Holdout must be 6 covered + 6 hard negatives")
    require(len({q["queryId"] for q in queries["queries"]}) == 12, "Duplicate holdout query IDs")
    for q in queries["queries"]:
        require(digest(q["questionText"].encode("utf-8")) == q["questionSha256"], "Question text SHA mismatch")
        require(q["embeddingQueryText"] == q["rerankQueryText"] == q["questionText"], "Question-only query contract")
        require(len(q["questionText"].encode("utf-8")) <= protocol["maxQueryUtf8Bytes"], "Query byte gate")
    for path, sha in queries["corpusDocumentSha256"].items():
        require(digest((root / path).read_bytes()) == sha, "Frozen source document changed")
    require(config["minimumRelevanceScore"] == .5 and config["maxEvidence"] == 2 and not config["backfillFromRank3"], "Rule gate")
    return queries, config, protocol


def history_paths(root):
    roots = [root / "target" / name for name in (
        "local-rag-p0-codex-20261011-v1", "local-rag-p0-codex-20261011-v2-perfmark",
        "local-rag-live-runs", "rag-e2e", "rag-p1-independent", "rag-p1-20261012",
        "rag-p1-closeout-20261012", "rag-p2")]
    roots += [root / "tools/rag-p1", root / "tools/rag-p2", root / "tools/rag-e2e",
              root / "docs/rag/source-documents", root / "src/test/resources/rag-p1-independent"]
    # Historical reports, responses, locks, scripts and original JSON. No code executes here.
    paths = {p.resolve() for base in roots if base.exists() for p in base.rglob("*") if p.is_file()
             and p.suffix.lower() not in (".class", ".jar", ".zip", ".pyc", ".png", ".pdf")}
    for source in read(root / "target/rag-p2/prepared/manifest.json")["inputs"].values():
        p = Path(source["originalPath"])
        if p.exists():
            paths.add(p.resolve())
    return sorted(paths, key=str)


def verify_history(root, history):
    for path, sha in history.items():
        require(digest(Path(path).read_bytes()) == sha, "P0/P1/P2 history changed; preserve evidence before continuing")
    # Missing files are also failures; never replace a historical lock or response.
    return True


def load_p2(root):
    require(digest((root / "target/rag-p2/run-once/results.json").read_bytes()) == P2_RESULT_SHA, "P2 real result SHA mismatch")
    dataset, labels, plan = p2.verify_prepared(root)
    result = p2.verify_saved_run(root, dataset, plan)
    require(result["P2_RERANK_RESULT"] == "PASS" and result["retryCount"] == 0, "P2 real run must be complete")
    return dataset, labels, {q["queryId"]: q["rerankResults"] for q in result["queries"]}, result


def pick(q, ranked, strategy, config):
    """Labels, expected coverage and target mechanism never affect admission."""
    if strategy == STRATEGIES[0]:
        return q["rawTopK5"][:2]
    by_id = {h["vectorId"]: h for h in q["rawTopK5"]}
    pairs = [(r, by_id[r["vectorId"]]) for r in ranked[:2]]
    if strategy == STRATEGIES[2]:
        pairs = [(r, h) for r, h in pairs if r["relevanceScore"] >= config["minimumRelevanceScore"]]
    elif strategy == STRATEGIES[3]:
        pairs = [(r, h) for r, h in pairs if not p2.low_information(h["knowledge"]["content"])]
    return [h for _, h in pairs]


def covered(q):
    return q.get("expectedCoverage", q.get("coverageStatus")) in ("COVERED", "COVERED_BY_DOCUMENT_CONTENT")


def row_label(q, hit, labels, view):
    row = labels.get(p2.key(q, hit))
    if row is None or row.get("label") not in (0, 1, 2) or not p2.label_allowed(row, view):
        return None
    return row["label"]


def ratio(a, b):
    return a / b if b else None


def quality_metrics(dataset, labels, reranks, config):
    output = []
    for strategy in STRATEGIES:
        picks = {q["queryId"]: pick(q, reranks[q["queryId"]], strategy, config) for q in dataset}
        views = {}
        for view in VIEWS:
            pool, kept, baseline = Counter(), Counter(), Counter()
            rows = []
            for q in dataset:
                all_known = [row_label(q, h, labels, view) for h in q["rawTopK5"]]
                retained = [row_label(q, h, labels, view) for h in picks[q["queryId"]]]
                original_top2 = pick(q, reranks[q["queryId"]], STRATEGIES[1], config)
                baseline_labels = [row_label(q, h, labels, view) for h in original_top2]
                pool.update(v for v in all_known if v is not None)
                kept.update(v for v in retained if v is not None)
                baseline.update(v for v in baseline_labels if v is not None)
                if any(v is not None for v in all_known):
                    rows.append({"queryId": q["queryId"], "covered": covered(q),
                                 "anyKnownUseful": any(v in (1, 2) for v in retained), "anyKnownDirect": 2 in retained,
                                 "fullyKnown": all(v is not None for v in all_known), "evidenceCount": len(retained)})
            c = [r for r in rows if r["covered"] and r["fullyKnown"]]
            n = [r for r in rows if not r["covered"] and r["fullyKnown"]]
            useful_pool, useful_kept = pool[1] + pool[2], kept[1] + kept[2]
            views[view] = {
                "status": "HUMAN_ONLY_PARTIAL" if view == "HUMAN_ONLY" else "PROVISIONAL",
                "knownPool": sum(pool.values()), "selectedKnown": sum(kept.values()),
                "direct2": kept[2], "partial1": kept[1], "irrelevant0": kept[0],
                "usefulRetentionWithinTop5": ratio(useful_kept, useful_pool),
                "directRetentionWithinTop5": ratio(kept[2], pool[2]),
                "usefulDeletedWithinTop5": useful_pool - useful_kept,
                "usefulDeletionRateWithinTop5": ratio(useful_pool - useful_kept, useful_pool),
                "irrelevantAdmissionRate": ratio(kept[0], sum(kept.values())),
                "usefulRetentionRelativeToFixedRerankTop2": ratio(useful_kept, baseline[1] + baseline[2]),
                "directRetentionRelativeToFixedRerankTop2": ratio(kept[2], baseline[2]),
                "coveredUsefulQueries": sum(r["anyKnownUseful"] for r in c), "coveredDirectQueries": sum(r["anyKnownDirect"] for r in c),
                "coveredFalseRejects": sum(not r["anyKnownUseful"] for r in c), "coveredEvaluableDenominator": len(c),
                "outsideCorrectRejects": sum(r["evidenceCount"] == 0 for r in n), "outsideEvaluableDenominator": len(n),
                "rows": rows,
            }
        output.append({"strategy": strategy, "selectedTotal": sum(len(v) for v in picks.values()),
                       "zeroOneTwoDistribution": {str(i): sum(len(v) == i for v in picks.values()) for i in range(3)},
                       "outsideZeroByPredeclaredCoverage": sum(not covered(q) and not picks[q["queryId"]] for q in dataset),
                       "coveredZeroByPredeclaredCoverage": sum(covered(q) and not picks[q["queryId"]] for q in dataset),
                       "views": views, "selectedIdsByQuery": {qid: [h["vectorId"] for h in hits] for qid, hits in picks.items()}})
    return output


def budget_plan(queries, protocol):
    embedding = sum(len(q["questionText"].encode("utf-8")) + 256 for q in queries["queries"])
    rerank = sum(5 * (len(q["questionText"].encode("utf-8")) + len(p2.INSTRUCT.encode("utf-8")) +
                      protocol["maxDocumentUtf8Bytes"] + protocol["framingTokenByteReservePerPair"])
                 for q in queries["queries"])
    price = Decimal(protocol["rerankPriceCnyPerMillion"])
    conservative = price * Decimal(embedding + rerank) / Decimal(1000000)
    require(conservative < Decimal(protocol["budgetReferenceCny"]), "Preregistered budget reference exceeded")
    return {"maxCalls": protocol["maxCalls"], "embeddingTokenByteProxy": embedding, "rerankWorstCaseTokenByteProxy": rerank,
            "conservativeCombinedCostProxyCny": str(conservative), "budgetReferenceCny": protocol["budgetReferenceCny"],
            "historicalP2ScaledRerankCostReferenceCny": "0.01611225",
            "referenceCaveat": "P2 42966 tokens * 12/16; new candidates unknown; UTF8 proxies are not actual tokens or invoice caps"}


def freeze(root, queries, protocol):
    prepared = root / "target/rag-p3/prepared"
    prepared.mkdir(parents=True, exist_ok=True)
    path = prepared / "freeze.json"
    if path.exists():
        existing = read(path)
        require(existing["coreSha256"] == CORE, "Frozen rule/query/protocol changed; no in-place retuning")
        require(digest(path.read_bytes()) == (prepared / "freeze.sha256").read_text("ascii").strip(), "Freeze seal changed")
        verify_history(root, existing["historySha256"])
        return existing
    history = {str(p): digest(p.read_bytes()) for p in history_paths(root)}
    state = {"schemaVersion": "rag-p3-preregistration-freeze-v1", "frozenUtc": p2.utc(), "coreSha256": CORE,
             "historySha256": history, "p2RealResultSha256": P2_RESULT_SHA,
             "corpusDocumentSha256": queries["corpusDocumentSha256"], "budget": budget_plan(queries, protocol),
             "nextRealResultStatusAtFreeze": "NOT_RUN", "ruleSelectedFrom": "P2_IN_SAMPLE_EXPLORATORY_ONLY"}
    with path.open("xb") as stream:
        stream.write(encode(state))
    (prepared / "freeze.sha256").write_text(digest(path.read_bytes()) + "\n", encoding="ascii")
    for name, sha in CORE.items():
        seal = root / "tools/rag-p3" / (name + ".sha256")
        if not seal.exists():
            seal.write_text(sha + "\n", encoding="ascii")
    return state


def verify(root):
    queries, config, protocol = load_core(root)
    prepared = root / "target/rag-p3/prepared"
    state = read(prepared / "freeze.json")
    require(state["coreSha256"] == CORE and digest((prepared / "freeze.json").read_bytes()) ==
            (prepared / "freeze.sha256").read_text("ascii").strip(), "Freeze SHA binding")
    verify_history(root, state["historySha256"])
    return queries, config, protocol, state


def render(directory, title, dataset, labels, reranks, config, status):
    metrics = quality_metrics(dataset, labels, reranks, config)
    write(directory / "comparison.json", {"status": status, "scoreCalibration": "NOT_CALIBRATED_PROBABILITY",
                                           "evidenceSufficiency": "NOT_ASSESSED", "metrics": metrics})
    lines = ["| 策略 | 保留 | 直接2 | 部分1 | 无关0 | 有用保留率/Top5 | 直接保留率/Top5 | 覆盖误拒 | 库外正确拒绝 |",
             "|---|---:|---:|---:|---:|---:|---:|---:|---:|"]
    for item in metrics:
        m = item["views"]["COMBINED_PROVISIONAL"]
        def percent(x):
            return f"{100*x:.2f}%" if x is not None else "待标签"
        lines.append(f"| {item['strategy']} | {item['selectedTotal']} | {m['direct2']} | {m['partial1']} | {m['irrelevant0']} | "
                     f"{percent(m['usefulRetentionWithinTop5'])} | {percent(m['directRetentionWithinTop5'])} | "
                     f"{m['coveredFalseRejects']}/{m['coveredEvaluableDenominator']} | {m['outsideCorrectRejects']}/{m['outsideEvaluableDenominator']} |")
    report = f"# {title}\n\n状态：**{status}**。全部旧标签保留来源；评分不是跨问题校准概率。\n\n" + "\n".join(lines)
    report += "\n\n分母Top5是此次检索候选池，不是完整语料召回率。相对固定Rerank Top2的保留率另外记录在comparison.json。未标记候选不当作0；人工/AI/合并三视图分开。\n"
    report += "\n历史P2的Q08第二条约0.418被0.50规则排除；纯低信息诊断不能排除有正文但目的不符的Chunk。历史Q02第一条人工标签1仍可能高分准入；相关分数不能使部分证据成为整题充分依据。未审核输出的充分性状态为NOT_ASSESSED，独立新题另收集人工SUFFICIENT/PARTIAL/NONE/U。\n"
    report += "\n0.50是在已有P2分数上选择的探索假设；题集、规则、验证协议在下一批结果前以freeze.json锁定。不扫描新holdout阈值，不看结果后修改同一协议并宣称PASS。\n"
    (directory / "report.md").write_text(report, encoding="utf-8")
    sections = []
    for q in dataset:
        ranks = {r["vectorId"]: r for r in reranks[q["queryId"]]}
        cards = []
        chosen = {s: {h["vectorId"] for h in pick(q, reranks[q["queryId"]], s, config)} for s in STRATEGIES}
        for h in q["rawTopK5"]:
            r, label = ranks[h["vectorId"]], labels.get(p2.key(q, h))
            label_text = f"{label['label']} / {label['source']}" if label else "UNLABELED"
            strategy_text = " · ".join(s[0] + (":留" if h["vectorId"] in chosen[s] else ":拒") for s in STRATEGIES)
            cards.append(f"<article><b>原Rank {h['rank']} → Rerank {r['rerankRank']} · {r['relevanceScore']:.6f}</b>"
                         f"<p>{strategy_text} · {html.escape(label_text)} · 充分性未评估</p><p>{html.escape(h['knowledge']['title'])}</p>"
                         f"<details><summary>Chunk {h['knowledge']['chunkId']} 完整正文</summary><pre>{html.escape(h['knowledge']['content'])}</pre></details></article>")
        sections.append(f"<section><h2>{q['queryId']}</h2><p>{html.escape(q['questionText'])}</p>{''.join(cards)}</section>")
    page = "<!doctype html><html lang='zh-CN'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
    page += "<style>body{font:15px/1.7 system-ui,'Microsoft YaHei';max-width:1120px;margin:auto;padding:24px;background:#f4f6fa;color:#17233a}section,header{background:white;padding:22px;margin:18px 0;border:1px solid #dde3ed;border-radius:10px}article{padding:12px;border-left:4px solid #3f6b90;margin:12px 0;background:#fafbfc}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}table{border-collapse:collapse;width:100%}td{padding:7px;border-bottom:1px solid #ddd}</style>"
    page += f"<title>RAG P3 {status}</title><header><h1>{html.escape(title)}</h1><p><b>{status}</b> · PROVISIONAL · 不等于整题证据充分</p><table>"
    for line in lines:
        if line.startswith("|---"):
            continue
        page += "<tr>" + "".join(f"<td>{html.escape(cell.strip())}</td>" for cell in line.strip("|").split("|")) + "</tr>"
    human_count = sum(row["source"] == "HUMAN_ORIGINAL" for row in labels.values())
    ai_count = sum(row["source"] == "AI_SUGGESTION" for row in labels.values())
    page += f"</table><p>人工标签{human_count}条/AI建议{ai_count}条分开；只扫描旧P2，新的12题阈值预先锁定。</p></header>" + "".join(sections) + "</html>"
    (directory / "review.html").write_text(page, encoding="utf-8")
    return metrics


def prepare(root):
    queries, config, protocol = load_core(root)
    dataset, labels, ranked, p2_result = load_p2(root)
    state = freeze(root, queries, protocol)
    directory = root / "target/rag-p3/prepared"
    render(directory, "P3：已有16题离线动态准入对照", dataset, labels, ranked, config, "IN_SAMPLE_EXPLORATORY")
    scan = []
    for threshold in (.01, .1, .3, .4, .5, .6, .7, .8, .85, .9, .95):
        trial = dict(config, minimumRelevanceScore=threshold)
        metric = quality_metrics(dataset, labels, ranked, trial)[2]
        scan.append({"threshold": threshold, "status": "IN_SAMPLE_EXPLORATORY", **metric})
    write(directory / "old-p2-score-scan.json", {"newHoldoutUsed": False, "selectedBeforeNewResults": .5, "scan": scan})
    write(directory / "manifest.json", {"P3_ADMISSION_PREP": "OFFLINE_IMPLEMENTED_TESTS_PENDING", "historyPreserved": True,
                                         "realCallsThisStage": {"embedding": 0, "milvusSearch": 0, "rerank": 0, "deepseek": 0, "jev": 0},
                                         "freezeSha256": digest((directory / "freeze.json").read_bytes()), "budget": state["budget"],
                                         "p2VerifiedUsage": p2_result["cost"], "productionChanged": False,
                                         "newQualityStatus": "NOT_RUN; COVERAGE_AUDIT_AI_PRE_RETRIEVAL, HUMAN_CONFIRMATION_REQUIRED"})
    return state


def verify_offline_tests(root):
    for name in ("python-tests.json", "java-tests.json", "powershell-tests.json"):
        data = read(root / "target/rag-p3/offline" / name)
        require(data.get("passed") is True and data.get("failures") == data.get("errors") == 0, "P3 offline tests must pass")
    data = read(root / "target/rag-p3/offline/python-tests.json")
    for path, sha in data["sourceSha256"].items():
        require(digest((root / path).read_bytes()) == sha, "P3 source changed after testing; rerun Offline")


def build_rerank_body(q, protocol):
    documents = [h["knowledge"]["title"] + "\n\n" + h["knowledge"]["content"] for h in q["rawTopK5"]]
    require(len(documents) == 5 and all(len(d.encode("utf-8")) <= protocol["maxDocumentUtf8Bytes"] for d in documents), "Rerank document byte gate")
    return {"model": protocol["rerankModel"], "input": {"query": q["questionText"], "documents": documents},
            "parameters": {"top_n": 5, "instruct": p2.INSTRUCT}}


def validate_retrieval(raw, queries, origin):
    require(raw["P3_RETRIEVAL_RESULT"] == "PASS" and raw["origin"] == origin, "Read-only retrieval did not pass")
    require(raw["calls"] == {"embedding": 12, "milvusSearch": 12, "deepseek": 0, "jev": 0, "mysqlWrites": 0, "milvusWrites": 0}, "Retrieval call contract")
    require(raw["unchanged191Rows"] is True and raw["unchanged52Vectors"] is True, "Frozen resource check missing")
    require(len(raw["queries"]) == 12, "Retrieval query count")
    output = []
    for q, actual in zip(queries["queries"], raw["queries"], strict=True):
        require(q["queryId"] == actual["queryId"] and q["questionText"] == actual["questionText"], "Retrieval question changed")
        require(actual["status"] == "COMPLETE" and len(actual["rawTopK5"]) == 5, "Need exactly five hydrated candidates")
        require([h["rank"] for h in actual["rawTopK5"]] == [1, 2, 3, 4, 5], "Raw ranks changed")
        require(len({h["vectorId"] for h in actual["rawTopK5"]}) == 5, "Duplicate vector IDs")
        require(type(actual["embedding"]["usage"]["total_tokens"]) is int and actual["embedding"]["usage"]["total_tokens"] > 0,
                "Embedding usage missing")
        for hit in actual["rawTopK5"]:
            require(hit["sqlEligible"] is True and hit["knowledge"]["vectorId"] == hit["vectorId"], "Ineligible candidate")
            require(digest(hit["knowledge"]["content"].encode("utf-8")) == hit["contentSha256"], "Hydrated content SHA mismatch")
            require(math.isfinite(hit["cosineFloat32"]) and struct.pack(">f", hit["cosineFloat32"]).hex() == hit["cosineFloat32Bits"], "COSINE bits mismatch")
        output.append(dict(actual, expectedCoverage=q["expectedCoverage"]))
    return output


def java_retrieval(root, credentials):
    build = root / "target/rag-p3/offline/classes"
    cp = str(build) + ";" + str(root / "target/classes") + ";" + (root / "target/rag-e2e/runtime-classpath.txt").read_text("utf-8-sig").strip()
    java = Path("C:/Program Files/Java/jdk-21.0.11/bin/java.exe")
    environment = {n: os.environ[n] for n in ("SystemRoot", "WINDIR", "COMSPEC", "TEMP", "TMP", "USERPROFILE") if n in os.environ}
    child = subprocess.run([str(java), "-Djdk.httpclient.disableRetryConnect=true", "-Djdk.httpclient.enableAllMethodRetry=false",
                            "-cp", cp, "P3ReadOnlyRetrieval", "execute-once"], input=encode(credentials),
                           cwd=root, env=environment, capture_output=True, timeout=600, check=False)
    require(child.returncode == 0, "Java retrieval stopped; preserve intent and raw response; no retry")
    return read(root / "target/rag-p3/run-once/retrieval/retrieval.json")


def cost(result, protocol):
    successes = [q for q in result["queries"] if q["status"] == "COMPLETE"]
    embedding = result.get("embeddingTokensKnown", 0)
    rerank = sum(q["usage"]["prompt_tokens"] for q in successes)
    amount = (Decimal(embedding) * Decimal(protocol["embeddingPriceCnyPerMillion"]) +
              Decimal(rerank) * Decimal(protocol["rerankPriceCnyPerMillion"])) / Decimal(1000000)
    result["cost"] = {"embeddingTokensKnown": embedding, "rerankPromptTokensKnown": rerank,
                      "estimatedGrossCnyKnown": str(amount), "actualInvoiceCny": None,
                      "complete": result.get("embeddingComplete") is True and len(successes) == 12,
                      "possibleUnaccountedCharge": result.get("embeddingComplete") is not True or
                      any(q["status"] in ("INTENT_RESERVED", "FAILED") for q in result["queries"])}


def execute(root, credentials, retrieval_runner=java_retrieval, transport=p2.post_once, origin="REAL"):
    queries, config, protocol, state = verify(root)
    if origin == "REAL":
        verify_offline_tests(root)
    seal = digest((root / "target/rag-p3/prepared/freeze.json").read_bytes())
    require(credentials.get("authorization") == AUTHORIZATION and credentials.get("freezeSha256") == seal, "One-time authorization/freeze binding")
    url = p2.endpoint(credentials.get("workspaceId"))
    for name in ("apiKey", "embeddingKey", "dbPassword"):
        require(isinstance(credentials.get(name), str) and credentials[name].strip(), "Missing local credential")
    folder = root / "target/rag-p3/run-once"
    folder.mkdir(exist_ok=False)
    (folder / "EXECUTION_RESERVED.lock").write_text(p2.utc(), encoding="ascii")
    result = {"P3_EXECUTION_RESULT": "IN_PROGRESS", "P3_QUALITY_RESULT": "PENDING_HUMAN_HOLDOUT_LABELS",
              "origin": origin, "startedUtc": p2.utc(), "freezeSha256": seal, "rerankAttempts": 0, "retryCount": 0,
              "queries": [{"queryId": q["queryId"], "questionText": q["questionText"], "status": "NOT_STARTED"} for q in queries["queries"]]}
    cost(result, protocol)
    write(folder / "results.json", result)
    try:
        retrieval = retrieval_runner(root, credentials)
        dataset = validate_retrieval(retrieval, queries, origin)
        result["embeddingTokensKnown"] = sum(q["embedding"]["usage"]["total_tokens"] for q in dataset)
        result["embeddingComplete"] = True
        result["retrievalSha256"] = digest((folder / "retrieval/retrieval.json").read_bytes())
        for q, record in zip(dataset, result["queries"], strict=True):
            require(result["rerankAttempts"] < 12, "Rerank attempt budget 12")
            cost(result, protocol)
            index = result["rerankAttempts"]
            remaining = sum(5 * (len(x["questionText"].encode("utf-8")) + len(p2.INSTRUCT) +
                                  protocol["maxDocumentUtf8Bytes"] + 256) for x in queries["queries"][index:])
            require(Decimal(result["cost"]["estimatedGrossCnyKnown"]) + Decimal(remaining) * Decimal(".50") / Decimal(1000000)
                    <= Decimal(protocol["budgetReferenceCny"]), "Runtime budget reference gate")
            body = build_rerank_body(q, protocol)
            record.update(status="INTENT_RESERVED", attempt=1, startedUtc=p2.utc(), requestSha256=digest(encode(body)))
            result["rerankAttempts"] += 1
            write(folder / f"{q['queryId']}.rerank-request.json", body)
            write(folder / "results.json", result)
            start = time.perf_counter()
            try:
                status, response = transport(url, credentials["apiKey"], encode(body))
                record.update(httpStatus=status, responseSha256=digest(response), elapsedMs=round((time.perf_counter() - start) * 1000, 3))
                sanitized = response
                for secret in (credentials["apiKey"], credentials["embeddingKey"], credentials["dbPassword"]):
                    sanitized = sanitized.replace(secret.encode("utf-8"), b"[REDACTED]")
                (folder / f"{q['queryId']}.rerank-response.json").write_bytes(sanitized)
                record["responseRedacted"] = sanitized != response
                require(status == 200, f"Rerank HTTP_{status}; no retry/fallback")
                require(sanitized == response, "Provider echoed credentials; raw response sanitized; stop")
                mapped, usage, request_id = p2.validate_response(response, q)
                record.update(status="COMPLETE", rerankResults=mapped, usage=usage, requestId=request_id,
                              endedUtc=p2.utc(), evidenceSufficiency="NOT_ASSESSED")
            except BaseException as exc:
                record.update(status="FAILED", failureType=type(exc).__name__, failureReason="STOP_NO_RETRY; billing outcome may be unknown")
                raise
            finally:
                cost(result, protocol)
                write(folder / "results.json", result)
        require(Decimal(result["cost"]["estimatedGrossCnyKnown"]) <= Decimal(protocol["budgetReferenceCny"]), "Final budget reference gate")
        verify_history(root, state["historySha256"])
        result["P3_EXECUTION_RESULT"] = "PASS" if origin == "REAL" else "OFFLINE_MOCK_COMPLETE"
        result["unchanged191Rows"] = result["unchanged52Vectors"] = result["historyPreserved"] = True
        result["queryServiceWorkMsById"] = {q["queryId"]: q["retrievalElapsedMs"] + r["elapsedMs"]
                                            for q, r in zip(dataset, result["queries"], strict=True)}
        latencies = [r["elapsedMs"] for r in result["queries"]]
        result["latency"] = {"rerankMeanMs": statistics.mean(latencies), "rerankMedianMs": statistics.median(latencies),
                             "rerankP95NearestRankMs": sorted(latencies)[math.ceil(.95 * len(latencies)) - 1],
                             "queryServiceWorkMeanMs": statistics.mean(result["queryServiceWorkMsById"].values()),
                             "definition": "Query work = Embedding+Search+hydration + Rerank; excludes batch-stage waiting and frozen-resource gates"}
        ranked = {r["queryId"]: r["rerankResults"] for r in result["queries"]}
        render(folder, "P3：独立12题验证（等待人工标签）", dataset, {}, ranked, config, "HOLDOUT_EXECUTED_QUALITY_UNLABELED")
        template = {"format": "p3-human-and-ai-labels-v1", "freezeSha256": seal,
                    "humanLabels": {p2.key(q, h): {"label": "U", "note": ""} for q in dataset for h in q["rawTopK5"]},
                    "aiSuggestions": {}, "coverageConfirmation": {q["queryId"]: "U" for q in dataset},
                    "primaryEvidenceSufficiency": {q["queryId"]: "U" for q in dataset}}
        write(folder / "labels-template.json", template)
    except BaseException as exc:
        partial = folder / "retrieval/retrieval.json"
        if partial.exists() and not result.get("embeddingComplete"):
            partial_data = read(partial)
            if type(partial_data.get("embeddingTokensKnown")) is int and partial_data["embeddingTokensKnown"] >= 0:
                result["embeddingTokensKnown"] = partial_data["embeddingTokensKnown"]
            result["retrievalCallsObserved"] = partial_data.get("calls")
        result["P3_EXECUTION_RESULT"] = "FAIL" if origin == "REAL" else "OFFLINE_MOCK_FAILED"
        result["blockedReason"] = str(exc) if isinstance(exc, p2.GateError) else type(exc).__name__
        raise p2.GateError("P3 stopped; preserve run-once and retrieval intent; no retry") from exc
    finally:
        result["endedUtc"] = p2.utc()
        cost(result, protocol)
        write(folder / "results.json", result)
        (folder / "results.sha256").write_text(digest((folder / "results.json").read_bytes()) + "\n", encoding="ascii")
    return result


def formal_quality(dataset, labels, ranked, confirmations, sufficiency, config, protocol):
    metrics = quality_metrics(dataset, labels, ranked, config)
    expected = {q["queryId"]: ("COVERED" if covered(q) else "NOT_COVERED") for q in dataset}
    if any(confirmations.get(qid) not in ("COVERED", "NOT_COVERED") for qid in expected):
        return "PENDING_HUMAN_COVERAGE", metrics
    if confirmations != expected:
        return "BLOCKED_COVERAGE_CHALLENGE", metrics
    if any(sufficiency.get(qid) not in ("SUFFICIENT", "PARTIAL", "NONE") for qid in expected):
        return "PENDING_HUMAN_SUFFICIENCY", metrics
    if any((row := labels.get(p2.key(q, h))) is None or row["source"] != "HUMAN_ORIGINAL" or row["label"] not in (0, 1, 2)
           for q in dataset for h in q["rawTopK5"]):
        return "PENDING_ALL60_HUMAN_LABELS", metrics
    m = metrics[2]["views"]["HUMAN_ONLY"]
    a = protocol["qualityAcceptance"]
    passed = (m["coveredUsefulQueries"] >= a["coveredUsefulQueriesMinimum"] and
              m["coveredDirectQueries"] >= a["coveredDirectQueriesMinimum"] and
              m["outsideCorrectRejects"] >= a["outsideCorrectRejectMinimum"] and
              m["irrelevant0"] <= a["irrelevantChunksAdmittedMaximum"] and
              (m["usefulRetentionRelativeToFixedRerankTop2"] or 0) >= a["usefulRetentionRelativeToFixedRerankTop2Minimum"] and
              (m["directRetentionRelativeToFixedRerankTop2"] or 0) >= a["directRetentionRelativeToFixedRerankTop2Minimum"])
    return ("PASS_ON_THIS_12_CASE_HOLDOUT_ONLY" if passed else "FAIL_UNDER_PRELOCKED_RULE"), metrics


def analyze_holdout(root, label_file):
    queries, config, protocol, state = verify(root)
    folder = root / "target/rag-p3/run-once"
    raw = (folder / "results.json").read_bytes()
    require(digest(raw) == (folder / "results.sha256").read_text("ascii").strip(), "Saved P3 result changed")
    result = json.loads(raw)
    require(result["P3_EXECUTION_RESULT"] == "PASS" and result["origin"] == "REAL", "Need complete real holdout for quality analysis")
    require(result["freezeSha256"] == digest((root / "target/rag-p3/prepared/freeze.json").read_bytes()) and
            result["rerankAttempts"] == 12 and result["retryCount"] == 0 and len(result["queries"]) == 12,
            "Saved holdout freeze/count binding")
    require(digest((folder / "retrieval/retrieval.json").read_bytes()) == result["retrievalSha256"], "Saved retrieval changed")
    dataset = validate_retrieval(read(folder / "retrieval/retrieval.json"), queries, "REAL")
    ranked = {}
    for q, record in zip(dataset, result["queries"], strict=True):
        require(q["queryId"] == record["queryId"] and q["questionText"] == record["questionText"], "Saved holdout query mismatch")
        response = (folder / f"{q['queryId']}.rerank-response.json").read_bytes()
        require(digest(response) == record["responseSha256"] and record["status"] == "COMPLETE", "Saved response changed")
        require((folder / f"{q['queryId']}.rerank-request.json").read_bytes() == encode(build_rerank_body(q, protocol)), "Saved request changed")
        mapped, usage, request_id = p2.validate_response(response, q)
        require(mapped == record["rerankResults"] and usage == record["usage"] and request_id == record["requestId"], "Saved rerank mapping changed")
        ranked[q["queryId"]] = mapped
    recalculated = json.loads(raw)
    cost(recalculated, protocol)
    require(recalculated["cost"] == result["cost"], "Saved holdout cost accounting changed")
    source = read(label_file)
    require(source["freezeSha256"] == result["freezeSha256"], "Label freeze binding")
    valid = {p2.key(q, h) for q in dataset for h in q["rawTopK5"]}
    labels = {}
    for name, provenance in (("aiSuggestions", "AI_SUGGESTION"), ("humanLabels", "HUMAN_ORIGINAL")):
        for row_key, row in source.get(name, {}).items():
            require(row_key in valid and str(row["label"]) in ("0", "1", "2", "U"), "Invalid label/identity")
            # Human U stays unknown and does not silently become an AI-confirmed human label.
            if str(row["label"]) != "U":
                labels[row_key] = {"label": int(row["label"]), "source": provenance, "note": row.get("note", "")}
    outcome, metrics = formal_quality(dataset, labels, ranked, source.get("coverageConfirmation", {}),
                                      source.get("primaryEvidenceSufficiency", {}), config, protocol)
    directory = root / "target/rag-p3/quality-reviews" / (digest(Path(label_file).read_bytes())[:16])
    directory.mkdir(parents=True, exist_ok=True)
    render(directory, "P3：独立12题标签评估", dataset, labels, ranked, config, outcome)
    (directory / "labels-input.json").write_bytes(Path(label_file).read_bytes())
    write(directory / "quality.json", {"P3_QUALITY_RESULT": outcome, "scope": protocol["formalOutcomeScope"],
                                       "labelSourceSha256": digest(Path(label_file).read_bytes()), "metrics": metrics,
                                       "primaryEvidenceSufficiency": source.get("primaryEvidenceSufficiency", {}),
                                       "sufficiencyAutomaticallyInferred": False})
    return outcome


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["prepare", "verify", "ready", "execute-once", "analyze"])
    parser.add_argument("--labels", type=Path)
    args = parser.parse_args()
    try:
        if args.mode == "prepare":
            prepare(ROOT)
            print("P3_OFFLINE_DATA_PREPARED; no real calls")
        elif args.mode == "ready":
            verify(ROOT)
            verify_offline_tests(ROOT)
            manifest = read(ROOT / "target/rag-p3/prepared/manifest.json")
            manifest["P3_ADMISSION_PREP"] = "READY"
            write(ROOT / "target/rag-p3/prepared/manifest.json", manifest)
            print("P3_ADMISSION_PREP=READY")
        elif args.mode == "verify":
            verify(ROOT)
            print("P3_FROZEN_INPUTS=VERIFIED; no real calls")
        elif args.mode == "analyze":
            require(args.labels is not None, "Supply separately exported human/AI labels")
            print("P3_QUALITY_RESULT=" + analyze_holdout(ROOT, args.labels))
        else:
            result = execute(ROOT, json.loads(sys.stdin.read(65536)))
            print("P3_EXECUTION_RESULT=" + result["P3_EXECUTION_RESULT"] + "; P3_QUALITY_RESULT=PENDING_HUMAN_HOLDOUT_LABELS")
    except (p2.GateError, OSError, KeyError, json.JSONDecodeError) as exc:
        print("P3_ADMISSION_PREP=BLOCKED; " + (str(exc) if isinstance(exc, p2.GateError) else type(exc).__name__), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
