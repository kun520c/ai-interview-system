"""P2 experiment only. Standard library; no Spring, database or vector-store clients."""
import argparse
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
import hashlib
import html
import http.client
import json
import math
import os
from pathlib import Path
import re
import ssl
import statistics
import struct
import sys
import time

MODEL = "qwen3.7-text-rerank"
API_PATH = "/api/v1/services/rerank/text-rerank/text-rerank"
INSTRUCT = "Given a web search query, retrieve relevant passages that answer the query."
MAX_CALLS = 16
PRICE = Decimal("0.50")  # CNY per million input tokens; Beijing, checked 2026-10-13.
BUDGET = Decimal("0.50")
AUTHORIZATION = "AUTHORIZE P2 RERANK 16 ONCE"
ROOT = Path(__file__).resolve().parents[2]
FROZEN = {
    "results.json": "75cf6cf1ba1606e24ae651eb3b63133e09e906a44cf1ee110630ab7ae5dc39d7",
    "queries.json": "4f42970bbdb3682c475056e512ff725832329d213b8b49e3a27ac45b5afbd456",
    "human-annotations.json": "d0b39eff38818ad7a1f25e644be4871a657a0b42cd039466ab75742f82a8f9a8",
    "rag_p1_ai_review.html": "98bd6f403e6d04e4da77b81b3ccf463fbf662840d23a47fec3694ee4fe1a6683",
    "rag_p1_quality_review.md": "c7c60b8f93af4a006ba448d04ecf5c6412500d73c4c1074b73982e4a896ef5ad",
}
STRATEGIES = ["A_Milvus_Top5", "B_Milvus_Top3", "C_COSINE_0.70",
              "D_COSINE_0.60_HISTORICAL", "E_Rerank_Top1", "F_Rerank_Top2", "G_Rerank_Top3"]
LABEL_VIEWS = ["HUMAN_ONLY", "AI_ONLY_PROVISIONAL", "COMBINED_PROVISIONAL"]


class GateError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise GateError(message)


def utc():
    return datetime.now(timezone.utc).isoformat()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encode(data):
    return (json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")


def write_json(path, data):
    """Replace P2-owned outputs atomically; fsync intent before any paid request."""
    path = Path(path)
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("wb") as stream:
        stream.write(encode(data))
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(path)


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def key(query, hit):
    return f"{query['queryId']}/{hit['rank']}/{hit['vectorId']}"


def sources(root, downloads):
    return {
        "results.json": root / "target/rag-p1-independent/run-once/results.json",
        "queries.json": root / "src/test/resources/rag-p1-independent/queries-v1.json",
        **{name: downloads / name for name in FROZEN if name not in ("results.json", "queries.json")},
    }


def snapshot_inputs(root, downloads):
    inputs = root / "target/rag-p2/inputs"
    data, provenance = {}, {}
    # Read and verify ALL originals before creating any copies; never rewrite P1 inputs.
    for name, original in sources(root, downloads).items():
        payload = original.read_bytes()
        require(digest(payload) == FROZEN[name], f"Frozen input SHA256 mismatch: {name}")
        data[name] = payload
        provenance[name] = {"originalPath": str(original.resolve()), "sha256": FROZEN[name]}
    inputs.mkdir(parents=True, exist_ok=True)
    for name, payload in data.items():
        destination = inputs / name
        if destination.exists():
            require(digest(destination.read_bytes()) == FROZEN[name], f"P2 snapshot changed: {name}")
        else:
            with destination.open("xb") as stream:
                stream.write(payload)
        provenance[name]["snapshotPath"] = str(destination.resolve())
    return provenance


def load_dataset(inputs):
    data = {}
    for name, expected in FROZEN.items():
        payload = (inputs / name).read_bytes()
        require(digest(payload) == expected, f"Frozen snapshot SHA256 mismatch: {name}")
        data[name] = payload.decode("utf-8-sig").replace("\r\n", "\n")
    raw, queries, humans = [json.loads(data[n]) for n in
                           ("results.json", "queries.json", "human-annotations.json")]
    require(raw["INDEPENDENT_RETRIEVAL_RUN"] == "PASS", "P1 retrieval was not PASS")
    require(raw["datasetSha256"] == humans["datasetSha256"] == FROZEN["queries.json"], "Query SHA binding")
    require(humans["evidenceSha256"] == FROZEN["results.json"], "Human evidence SHA binding")
    require(len(raw["queries"]) == len(queries["queries"]) == 16, "Require exactly 16 queries")
    require(len(humans["humanLabels"]) == 10, "Require the original ten human labels")
    indexed_queries = {q["queryId"]: q for q in queries["queries"]}
    require(set(indexed_queries) == {f"P1I-Q{i:02}" for i in range(1, 17)}, "Unexpected query IDs")
    dataset = []
    for q in raw["queries"]:
        frozen_q = indexed_queries[q["queryId"]]
        require(q["questionText"] == frozen_q["questionText"], "Question text mismatch")
        require(q["testType"] == frozen_q["testType"] and q["status"] == "COMPLETE", "Query contract mismatch")
        require(len(q["rawTopK5"]) == 5, "Require all original five candidates")
        require([h["rank"] for h in q["rawTopK5"]] == [1, 2, 3, 4, 5], "Original rank changed")
        require(len({h["vectorId"] for h in q["rawTopK5"]}) == 5, "Duplicate candidate ID")
        for h in q["rawTopK5"]:
            k = h["knowledge"]
            require(h["sqlEligible"] is True and k["content"].strip(), "Missing eligible full text")
            require(h["vectorId"] == k["vectorId"], "Candidate identity mismatch")
            require(digest(k["content"].encode("utf-8")) == h["contentSha256"], "Chunk content changed")
            require(struct.pack(">f", h["cosineFloat32"]).hex() == h["cosineFloat32Bits"], "COSINE bits changed")
        dataset.append(dict(q, coverageStatus=frozen_q["coverageStatus"]))
    require(len({q["queryId"] for q in dataset}) == 16, "Duplicate result queries")
    require(Counter(q["coverageStatus"] for q in dataset) ==
            {"COVERED_BY_DOCUMENT_CONTENT": 12, "NOT_COVERED": 4}, "Coverage contract changed")
    labels = extract_labels(dataset, humans, data["rag_p1_ai_review.html"], data["rag_p1_quality_review.md"])
    return dataset, labels


def extract_labels(dataset, humans, review_html, review_md):
    """Read trusted-by-hash report as data; never execute its markup or instructions."""
    sections = dict(re.findall(r'<section class="query" id="(P1I-Q\d+)">(.*?)</section>', review_html, re.S))
    md_sections = dict(re.findall(r'### (P1I-Q\d+)[^\n]*\n(.*?)(?=\n### |\Z)', review_md, re.S))
    require(set(sections) == set(md_sections) == {q["queryId"] for q in dataset}, "Report query mismatch")
    labels = {}
    for q in dataset:
        articles = re.findall(r'<article class="hit l[012]">(.*?)</article>', sections[q["queryId"]], re.S)
        require(len(articles) == 5, "Report candidate count mismatch")
        md_labels = {int(rank): int(label) for rank, label in
                     re.findall(r'- Rank(\d) / Chunk\d+ / COSINE [0-9.]+：\*\*([012])\*\*', md_sections[q["queryId"]])}
        require(len(md_labels) == 5, "Markdown labels missing")
        for hit, article in zip(q["rawTopK5"], articles, strict=True):
            heading = re.search(r'<h3>Rank (\d) · 标签 ([012]) · (.*?)</h3>', article, re.S)
            identity = re.search(r'Chunk (\d+) · COSINE ([0-9.]+)', article)
            text = re.search(r'<div class="content">(.*?)</div>', article, re.S)
            rationale = re.search(r'<p><strong>理由：</strong>(.*?)</p>', article, re.S)
            source = re.search(r'<span class="(human|ai)">', article)
            require(all((heading, identity, text, rationale, source)), "Report row malformed")
            rank, label, title = heading.groups()
            require(int(rank) == hit["rank"] and int(label) == md_labels[hit["rank"]], "Report label/rank mismatch")
            require(identity[1] == hit["knowledge"]["chunkId"], "Report chunk ID mismatch")
            require(abs(float(identity[2]) - hit["cosineFloat32"]) <= 0.00000051, "Report rounded score mismatch")
            require(html.unescape(title) == hit["knowledge"]["title"], "Report title mismatch")
            require(html.unescape(text[1]) == hit["knowledge"]["content"], "Report full content mismatch")
            row_key = key(q, hit)
            human = humans["humanLabels"].get(row_key)
            require((source[1] == "human") == (human is not None), "Label provenance mismatch")
            if human is not None:
                require(human["label"] == label, "Original human label changed")
            labels[row_key] = {
                "queryId": q["queryId"], "originalRank": hit["rank"], "vectorId": hit["vectorId"],
                "chunkId": hit["knowledge"]["chunkId"], "label": int(label),
                "source": "HUMAN_ORIGINAL" if human else "AI_SUGGESTION",
                "humanOriginal": human, "rationale": html.unescape(rationale[1]),
                "reportLocator": f"rag_p1_ai_review.html#{q['queryId']}/Rank{rank}",
            }
    require(Counter(r["source"] for r in labels.values()) ==
            {"HUMAN_ORIGINAL": 10, "AI_SUGGESTION": 70}, "Require 10 human + 70 AI suggestions")
    require(set(humans["humanLabels"]).issubset(labels), "Orphan human labels")
    return labels


def make_plan(dataset):
    requests = []
    for q in dataset:
        documents = [h["knowledge"]["title"] + "\n\n" + h["knowledge"]["content"] for h in q["rawTopK5"]]
        body = {"model": MODEL, "input": {"query": q["questionText"], "documents": documents},
                "parameters": {"top_n": 5, "instruct": INSTRUCT}}
        # A deliberately conservative UTF-8 byte proxy, NOT measured/model tokens.
        # Query and instruction repeated per document, plus 256 framing reserve per pair.
        pair_proxies = [len((q["questionText"] + INSTRUCT + doc).encode("utf-8")) + 256 for doc in documents]
        require(max(pair_proxies) <= 30000 and sum(pair_proxies) <= 120000, "Offline input size gate")
        requests.append({"queryId": q["queryId"], "rerankQueryText": q["questionText"],
                         "body": body, "requestSha256": digest(encode(body)),
                         "inputTokenByteProxy": sum(pair_proxies),
                         "candidateIds": [{"inputIndex": i, "originalRank": h["rank"],
                                           "vectorId": h["vectorId"], "chunkId": h["knowledge"]["chunkId"],
                                           "contentSha256": h["contentSha256"]}
                                          for i, h in enumerate(q["rawTopK5"])]})
    require(len(requests) == MAX_CALLS, "Request count gate")
    proxy = sum(r["inputTokenByteProxy"] for r in requests)
    estimate = PRICE * Decimal(proxy) / Decimal(1000000)
    require(estimate <= BUDGET, "Conservative offline estimate exceeds authorization budget")
    return {"model": MODEL, "apiPath": API_PATH, "region": "cn-beijing", "maxHttpAttempts": MAX_CALLS,
            "authorizationBudgetCny": str(BUDGET), "priceCnyPerMillionInputTokens": str(PRICE),
            "inputTokenByteProxy": proxy, "conservativeCostProxyCny": str(estimate),
            "costCaveat": "UTF8 byte proxy + framing reserve, not provider tokenizer or guaranteed bill cap",
            "noRetry": True, "noRedirect": True, "queryPolicy": "EXACT_ORIGINAL_QUESTION_ONLY",
            "documentPolicy": "ORIGINAL_TITLE + TWO_NEWLINES + EXACT_FULL_CHUNK; NO LABELS OR ANSWER",
            "requests": requests}


def endpoint(workspace):
    require(isinstance(workspace, str) and re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?", workspace),
            "Missing/invalid Beijing Workspace ID; supply the ID from the console, not an Embedding URL")
    return f"https://{workspace}.cn-beijing.maas.aliyuncs.com{API_PATH}"


def validate_response(raw, q):
    try:
        response = json.loads(raw.decode("utf-8"), parse_constant=lambda s: (_ for _ in ()).throw(GateError("Non-finite JSON")))
        require(isinstance(response, dict) and not response.get("code"), "Provider returned an error")
        require(isinstance(response.get("request_id"), str) and response["request_id"].strip(), "Missing request ID")
        results = response["output"]["results"]
        require(isinstance(results, list) and len(results) == 5, "Response must return ALL five candidates")
        seen, mapped, previous = set(), [], 1.0
        for rank, result in enumerate(results, 1):
            index, score = result["index"], result["relevance_score"]
            require(type(index) is int and 0 <= index < 5 and index not in seen, "Invalid/duplicate result index")
            require(type(score) in (int, float) and math.isfinite(score) and 0 <= score <= 1, "Invalid relevance score")
            require(score <= previous, "Response scores are not descending")
            seen.add(index)
            previous = score
            h = q["rawTopK5"][index]
            mapped.append({"inputIndex": index, "originalRank": h["rank"], "rerankRank": rank,
                           "relevanceScore": score, "vectorId": h["vectorId"], "chunkId": h["knowledge"]["chunkId"],
                           "originalCosine": h["cosineFloat32"], "originalCosineBits": h["cosineFloat32Bits"],
                           "contentSha256": h["contentSha256"], "rankChange": h["rank"] - rank})
        usage = response["usage"]
        require(isinstance(usage, dict), "Missing trustworthy usage")
        require(all(type(usage.get(n)) is int and usage[n] > 0 for n in ("prompt_tokens", "total_tokens")),
                "Missing/invalid prompt_tokens or total_tokens")
        require(usage["total_tokens"] >= usage["prompt_tokens"], "Inconsistent token usage")
        return mapped, dict(usage), response["request_id"]
    except (KeyError, TypeError, UnicodeError, json.JSONDecodeError) as exc:
        raise GateError("Malformed response; stop without retry") from exc


def post_once(url, api_key, body):
    """One HTTPS POST; http.client has no HTTP-status/connection retry or redirects."""
    from urllib.parse import urlsplit
    parsed = urlsplit(url)
    require(parsed.scheme == "https" and parsed.path == API_PATH and not parsed.query and not parsed.fragment,
            "Rerank endpoint gate")
    require(re.fullmatch(r"[A-Za-z0-9-]+\.cn-beijing\.maas\.aliyuncs\.com", parsed.netloc), "Rerank host gate")
    connection = http.client.HTTPSConnection(parsed.netloc, timeout=30, context=ssl.create_default_context())
    try:
        connection.request("POST", API_PATH, body=body,
                           headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"})
        reply = connection.getresponse()
        payload = reply.read(2 * 1024 * 1024 + 1)
        require(len(payload) <= 2 * 1024 * 1024, "Response too large; stop without retry")
        return reply.status, payload
    finally:
        connection.close()


def selected(q, strategy, reranks):
    hits = q["rawTopK5"]
    if strategy.startswith("A_"):
        return hits
    if strategy.startswith("B_"):
        return hits[:3]
    if strategy.startswith(("C_", "D_")):
        # Preserve P1/Java semantics: decoded Float32 >= double 0.70 / 0.60, no rounding.
        threshold = 0.70 if strategy.startswith("C_") else 0.60
        return [h for h in hits if h["cosineFloat32"] >= threshold]
    if q["queryId"] not in reranks:
        return None
    by_id = {h["vectorId"]: h for h in hits}
    count = {"E": 1, "F": 2, "G": 3}[strategy[0]]
    return [by_id[r["vectorId"]] for r in reranks[q["queryId"]][:count]]


def label_allowed(row, view):
    return view == "COMBINED_PROVISIONAL" or row["source"] == (
        "HUMAN_ORIGINAL" if view == "HUMAN_ONLY" else "AI_SUGGESTION")


def ndcg(labels, k):
    def dcg(values):
        return sum((2 ** value - 1) / math.log2(i + 2) for i, value in enumerate(values[:k]))
    ideal = dcg(sorted(labels, reverse=True))
    return dcg(labels) / ideal if ideal else None


def metrics(dataset, labels, reranks):
    rows = []
    for strategy in STRATEGIES:
        picks = [selected(q, strategy, reranks) for q in dataset]
        if any(p is None for p in picks):
            rows.append({"strategy": strategy, "status": "PENDING_REAL_RERANK", "views": {}})
            continue
        views = {}
        for view in LABEL_VIEWS:
            distribution, removed, per_query = Counter(), Counter(), []
            for q, picked in zip(dataset, picks, strict=True):
                known = [h for h in q["rawTopK5"] if label_allowed(labels[key(q, h)], view)]
                if not known:
                    continue
                kept_ids = {h["vectorId"] for h in picked}
                kept_known = [h for h in picked if label_allowed(labels[key(q, h)], view)]
                kept_labels = [labels[key(q, h)]["label"] for h in kept_known]
                distribution.update(kept_labels)
                removed.update(labels[key(q, h)]["label"] for h in known if h["vectorId"] not in kept_ids)
                per_query.append({"queryId": q["queryId"], "coverageStatus": q["coverageStatus"],
                                  "retainedOriginalRanks": [h["rank"] for h in picked],
                                  "selectedKnown": len(kept_known), "usefulEvidence": any(x > 0 for x in kept_labels),
                                  "directEvidence": 2 in kept_labels, "selectedAnyCandidate": bool(picked)})
            kept = sum(distribution.values())
            useful = distribution[1] + distribution[2]
            covered = [r for r in per_query if r["coverageStatus"] == "COVERED_BY_DOCUMENT_CONTENT"]
            outside = [r for r in per_query if r["coverageStatus"] == "NOT_COVERED"]
            views[view] = {
                "status": "HUMAN_PARTIAL_10_ONLY" if view == "HUMAN_ONLY" else "PROVISIONAL",
                "knownCandidates": sum(label_allowed(r, view) for r in labels.values()),
                "selectedKnown": kept, "direct2": distribution[2], "partial1": distribution[1],
                "irrelevant0": distribution[0], "usefulDeleted": removed[1] + removed[2],
                "precisionUseful": useful / kept if kept else None,
                "precisionDirect": distribution[2] / kept if kept else None,
                "recallUsefulWithinOriginalTop5": useful / (useful + removed[1] + removed[2])
                if useful + removed[1] + removed[2] else None,
                "coveredQuestionsWithUseful": sum(r["usefulEvidence"] for r in covered),
                "coveredDenominator": len(covered),
                "outsideQuestionsAdmitted": sum(r["selectedAnyCandidate"] for r in outside),
                "outsideDenominator": len(outside), "queries": per_query,
            }
        rows.append({"strategy": strategy, "status": "COMPUTED", "selectedCandidates": sum(map(len, picks)), "views": views})
    return rows


def ranking_comparison(dataset, labels, reranks):
    output = {}
    for view in LABEL_VIEWS:
        comparisons, movements = [], []
        for q in dataset:
            if q["queryId"] not in reranks:
                continue
            ranks = {r["vectorId"]: r["rerankRank"] for r in reranks[q["queryId"]]}
            known = [h for h in q["rawTopK5"] if label_allowed(labels[key(q, h)], view)]
            for h in known:
                l = labels[key(q, h)]
                movements.append({"queryId": q["queryId"], "vectorId": h["vectorId"],
                                  "chunkId": h["knowledge"]["chunkId"], "label": l["label"], "source": l["source"],
                                  "originalRank": h["rank"], "rerankRank": ranks[h["vectorId"]]})
            if len(known) != 5:
                continue  # Never treat unlabelled candidates as irrelevant.
            original = [labels[key(q, h)]["label"] for h in q["rawTopK5"]]
            reranked = [labels[key(q, h)]["label"] for h in sorted(q["rawTopK5"], key=lambda h: ranks[h["vectorId"]])]
            comparisons.append({"queryId": q["queryId"], "originalNdcgAt3": ndcg(original, 3),
                                "rerankNdcgAt3": ndcg(reranked, 3),
                                "originalDirectAt1": original[0] == 2, "rerankDirectAt1": reranked[0] == 2,
                                "originalDirectAt2": 2 in original[:2], "rerankDirectAt2": 2 in reranked[:2]})
        measured = [c for c in comparisons if c["originalNdcgAt3"] is not None]
        deltas = [c["rerankNdcgAt3"] - c["originalNdcgAt3"] for c in measured]
        direct = [m for m in movements if m["label"] == 2]
        irrelevant = [m for m in movements if m["label"] == 0]
        output[view] = {
            "status": "PENDING_REAL_RERANK" if not comparisons else "PROVISIONAL_SMALL_SAMPLE",
            "ndcgAt3QueryCount": len(measured), "zeroRelevantExcluded": len(comparisons) - len(measured),
            "originalMeanNdcgAt3": statistics.mean(c["originalNdcgAt3"] for c in measured) if measured else None,
            "rerankMeanNdcgAt3": statistics.mean(c["rerankNdcgAt3"] for c in measured) if measured else None,
            "meanPairedNdcgAt3Delta": statistics.mean(deltas) if deltas else None,
            "pairedWins": sum(d > 1e-12 for d in deltas), "pairedLosses": sum(d < -1e-12 for d in deltas),
            "pairedTies": sum(abs(d) <= 1e-12 for d in deltas),
            "directInOriginalTop1": sum(m["originalRank"] == 1 for m in direct),
            "directInRerankTop1": sum(m["rerankRank"] == 1 for m in direct),
            "directInOriginalTop2": sum(m["originalRank"] <= 2 for m in direct),
            "directInRerankTop2": sum(m["rerankRank"] <= 2 for m in direct),
            "irrelevantDemoted": sum(m["rerankRank"] > m["originalRank"] for m in irrelevant),
            "irrelevantPromoted": sum(m["rerankRank"] < m["originalRank"] for m in irrelevant),
            "directCandidates": len(direct), "irrelevantCandidates": len(irrelevant),
            "queries": comparisons, "movements": movements,
        }
    return output


def low_information(text):
    """Conservative diagnostic flag only. Do not remove inputs in the first experiment."""
    reference = re.compile(r"^(?:\[[^\]]+\]:\s*https?://|[-*]\s*\[[^\]]+\]\s|https?://)")
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    refs = [line for line in lines if reference.match(line)]
    prose = [line for line in lines if not reference.match(line) and not line.startswith(("#", ">"))
             and not re.fullmatch(r"\d+(?:\.\d+)*[）)]?", line)]
    return bool(refs) and not prose


def admitted_evidence(ordered_hits, semantic_checks):
    """Research seam: external independent booleans, no COSINE/label/score oracle."""
    require(all(type(v) is bool for v in semantic_checks.values()), "Semantic checks must be explicit booleans")
    return [h for h in ordered_hits if semantic_checks.get(h["vectorId"], False) and
            not low_information(h["knowledge"]["content"])][:3]


def analysis(dataset, labels, reranks=None):
    reranks = reranks or {}
    return {"evaluationStatus": "PROVISIONAL", "humanLabelCount": 10, "aiSuggestionCount": 70,
            "humanGroundTruthCount": 10, "aiConfirmedForExploration": True,
            "generalizationVerified": False, "productionIntegration": False,
            "metrics": metrics(dataset, labels, reranks),
            "rankingComparison": ranking_comparison(dataset, labels, reranks),
            "lowInformationDiagnostics": [{"queryId": q["queryId"], "originalRank": h["rank"],
                                            "vectorId": h["vectorId"], "chunkId": h["knowledge"]["chunkId"]}
                                           for q in dataset for h in q["rawTopK5"] if low_information(h["knowledge"]["content"])],
            "metricDefinitions": {"useful": "label 1 or 2", "direct": "label 2",
                                  "ndcg": "gain 2^label-1; @3 over fully labelled queries; zero-positive queries excluded",
                                  "recall": "Recall within these original Top5 candidates, not corpus recall",
                                  "outsideAdmission": "Any selected candidate on a predeclared NOT_COVERED query",
                                  "humanOnly": "Only 10 original labels/2 queries; other candidates unknown, never label 0"}}


ADMISSION_RESEARCH = """排序与准入须分开验证。固定 Top1/2/3 总会选出候选，不能识别全不相关问题；官方 relevance_score 是请求内相对分数，不直接设置统一阈值。

| 可行方案 | 如何判断能否回答问题 | 优点与限制 | 下一步验证 |
|---|---|---|---|
| 独立相关性二分类/证据充分性判定 | 要求输出可回答的子问题、正文支持句；无支持返回 false | 可拒绝关键词重合；二分类仍可能误判，部分相关不等于充分，增加模型成本 | 在独立新题上人工审核误准入/误拒绝；本轮不调用第二模型 |
| Rerank 后小规模语义检查 | 检查最多 Top3 是否包含该问题的解释机制，数据库/版本/主题是否一致 | 限制额外 Token；候选被排序漏掉后无法救回 | 固定检查准则后验证新题；记录附加调用成本 |
| 去除纯链接、参考列表 | 仅标记缺乏解释正文的 Chunk；含机制和引用的正文保留 | 零模型成本，可清除低信息；不能排除有正文的语义漂移 | 先人工复核诊断标志；本轮所有原 Chunk 仍原样送入 |
| 动态输出 0～3 条 | 对排序候选执行独立有效性检查，通过者最多3条，全不通过返回0 | 保留拒绝能力；不能用标签/题目覆盖标记做线上准入 oracle | 研究函数只接收独立布尔检查结果，缺失检查默认不准入；未接生产 |

优先研究“解释正文检查 + Rerank Top3 + 独立语义有效性检查”，检查是否真正提供答案依据，而非共享技术词。语义检查必须区分部分有用与整题证据充分。纯链接、错误数据库机制、不同问题目的应单独统计。本批16题仅用于探索；不在这批题上反复调参后声称独立泛化。冻结研究选择后另建未看过结果的新题与人工标签，检验库外误准入、覆盖题误拒绝和证据充分率。
"""


def render_report(directory, dataset, labels, plan, result=None):
    reranks = {q["queryId"]: q["rerankResults"] for q in result["queries"] if q["status"] == "COMPLETE"} if result else {}
    a = analysis(dataset, labels, reranks)
    write_json(directory / "analysis.json", a)
    state = "PENDING_REAL_RERANK" if result is None else result["P2_RERANK_RESULT"]
    table = ["| 策略 | 保留 | 直接2 | 部分1 | 无关0 | 有用误删 | 覆盖题有用/12 | 库外准入/4 |",
             "|---|---:|---:|---:|---:|---:|---:|---:|"]
    for row in a["metrics"]:
        if row["status"] != "COMPUTED":
            table.append(f"| {row['strategy']} | 等待真实 Rerank | — | — | — | — | — | — |")
            continue
        m = row["views"]["COMBINED_PROVISIONAL"]
        table.append(f"| {row['strategy']} | {row['selectedCandidates']} | {m['direct2']} | {m['partial1']} | {m['irrelevant0']} | {m['usefulDeleted']} | {m['coveredQuestionsWithUseful']}/12 | {m['outsideQuestionsAdmitted']}/4 |")
    fee = json.dumps(result["cost"], ensure_ascii=False) if result else "未请求：实际 usage、延迟和费用尚无数据。"
    report = f"""# RAG P2 语义重排序与证据筛选实验

P2_RERANK_PREP=READY。P2_RERANK_RESULT={state}。
生成时间 UTC：{utc()}。10条原人工标签 + 70条 AI 建议；用户确认用于探索，仍为 **PROVISIONAL**，不是80条人工 Ground Truth，也不是独立泛化成功。

## 接口与运行约束

模型 `{MODEL}`；北京原生接口 `https://{{WorkspaceId}}.cn-beijing.maas.aliyuncs.com{API_PATH}`，`Authorization: Bearer <API Key>`。嵌套 input.query/documents 与 parameters.top_n=5/instruct。不发送 return_documents（此模型文档未支持），完整正文保存在本地。
Workspace ID 从百炼北京业务空间管理页取得，Key 必须有该空间及该模型权限；当前没有调用模型验证权限。入口交互输入ID并显示实际URL，不从Embedding URL猜测ID。401/403/模型不支持即停止、记录BLOCKED原因，不替换模型、不重试。

每题只使用原始 questionText；请求正文仅原必要标题 + 两个换行 + 完整 Chunk。16题各一次、全部5条返回、最多16次HTTP、顺序执行、30秒 socket 超时、无重试、无重定向。固定目录原子保留，失败/中断仍占用次数，重新运行拒绝已有目录。无续跑/删除锁入口。

0次 Embedding、Milvus Search、DeepSeek、Jev、数据库连接和数据库/向量写入；不启动Docker或Spring，不修改生产默认路径。

北京原价 ¥{PRICE}/百万输入Token；授权预算参考 ¥{BUDGET}。UTF-8保守代理 {plan['inputTokenByteProxy']}（包含逐候选重复Query/instruct及framing reserve），费用代理 ¥{plan['conservativeCostProxyCny']}。这是预检参考而非实际token计数或账单硬上限。执行时按实际 prompt_tokens 估算原价，单独记录total_tokens；usage缺失或失败请求可能收费时不得声称完整费用。每次请求前检查已知费用加余下保守代理；异常立即停。

实际数据：{fee}

## 策略对比：人工10 + AI70，PROVISIONAL

{chr(10).join(table)}

analysis.json同时给出HUMAN_ONLY（仅2道题10条，不能评价4道库外题）、AI_ONLY_PROVISIONAL和COMBINED_PROVISIONAL。提供precision、候选内useful recall、误删、覆盖命中、库外准入、nDCG@3及逐题配对增减，逐条label0升降和label2升至Rank1/2。不把未标记候选当0，不把条件跳过/Mock当真实结果。零正例题不计nDCG，独立统计库外准入。

当前尚无真实重排序，不能判断是否显著优于Top3/COSINE策略；结果产生后仍只作暂定质量比较，16题样本不支持正式精度提升主张。执行PASS仅表示16题响应/映射/usage完整，不表示相关性准入或泛化PASS。

## 独立语义准入研究

{ADMISSION_RESEARCH}

## 不可变证据与输出

原始结果SHA256：{FROZEN['results.json']}。
问题集SHA256：{FROZEN['queries.json']}。
5份源文件的字节副本及SHA在 ../inputs 与 prepared/manifest.json；人工note逐字保留。AI标签从旧HTML读取，与旧Markdown及原正文/题号/Rank/ChunkID交叉核对。所有旧文件保持原字节。
prepared/requests.json记录确切请求文本与index/vectorId/chunkId映射；labels.json保留标签来源；analysis.json输出三种标签视图；review.html逐题对照所有80条全文。真实运行输出固定 target/rag-p2/run-once：results.json、各题 request/response、analysis.json、report.md、review.html。失败保留原响应或脱敏响应与SHA、intent和未开始状态，不自动续跑。

官方来源（2026-10-13核对）：[Rerank接口](https://help.aliyun.com/zh/model-studio/text-rerank-api)、[模型与北京价格](https://help.aliyun.com/zh/model-studio/qwen3-7-text-rerank)、[请求长度计算](https://help.aliyun.com/zh/model-studio/rerank)、[API Key与Workspace](https://help.aliyun.com/zh/model-studio/first-api-call-to-qwen)。
"""
    (directory / "report.md").write_text(report, encoding="utf-8")
    metric_rows = "".join("<tr>" + "".join(f"<td>{html.escape(x.strip())}</td>" for x in line.strip("|").split("|")) + "</tr>"
                          for line in table if not line.startswith("|---"))
    sections = []
    for q in dataset:
        mapped = {r["vectorId"]: r for r in reranks.get(q["queryId"], [])}
        cards = []
        for h in q["rawTopK5"]:
            l, r = labels[key(q, h)], mapped.get(h["vectorId"])
            meta = f"原Rank {h['rank']} → Rerank {r['rerankRank']} / relevance {r['relevanceScore']}" if r else f"原Rank {h['rank']} → 待真实Rerank"
            cards.append(f"<article class='l{l['label']}'><h3>{html.escape(meta)}</h3><p>{html.escape(h['knowledge']['title'])}</p>"
                         f"<p>Chunk {h['knowledge']['chunkId']} · vectorId {h['vectorId']} · COSINE {h['cosineFloat32']}</p>"
                         f"<p>标签 {l['label']} · {l['source']} · {html.escape(l['rationale'])}</p>"
                         f"<p>人工原备注：{html.escape(l['humanOriginal']['note']) if l['humanOriginal'] else '—'}</p>"
                         f"<details><summary>完整原始 Chunk</summary><pre>{html.escape(h['knowledge']['content'])}</pre></details></article>")
        sections.append(f"<section><h2>{q['queryId']} · {q['coverageStatus']}</h2><p>{html.escape(q['questionText'])}</p>"
                        f"<small>Rerank Query = 上述原题面；不使用Embedding评分点扩写</small>{''.join(cards)}</section>")
    page = f"""<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>RAG P2 · PROVISIONAL</title><style>body{{font:15px/1.7 system-ui,'Microsoft YaHei';background:#f4f6fa;color:#17233a;max-width:1120px;margin:auto;padding:24px}}section,header{{background:white;padding:24px;border:1px solid #d8e0ed;border-radius:12px;margin:20px 0}}article{{border-left:5px solid #b65060;background:#fafbfe;padding:14px;margin:14px 0}}.l2{{border-color:#248566}}.l1{{border-color:#c0963d}}table{{width:100%;border-collapse:collapse}}td{{padding:7px;border-bottom:1px solid #ddd}}pre{{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}}small{{color:#5d697b}}</style>
<header><h1>RAG P2 语义排序实验</h1><p><b>PROVISIONAL · 原人工10条 + AI建议70条，保留来源</b></p><p>状态 {state}；尚无独立泛化证明；生产接入=false。</p><p>16×5固定候选；零数据库/向量写入。费用代理 ¥{plan['conservativeCostProxyCny']}；预算参考 ¥{BUDGET}。</p><table>{metric_rows}</table><p>固定Rerank Top1/2/3仍会给库外题选出候选；排序改善不等于零相关拒绝能力。HUMAN_ONLY/AI_ONLY分组与排序指标见analysis.json。</p></header>{''.join(sections)}</html>"""
    (directory / "review.html").write_text(page, encoding="utf-8")
    return a


def prepare(root, downloads):
    provenance = snapshot_inputs(root, downloads)
    dataset, labels = load_dataset(root / "target/rag-p2/inputs")
    plan = make_plan(dataset)
    directory = root / "target/rag-p2/prepared"
    directory.mkdir(parents=True, exist_ok=True)
    write_json(directory / "requests.json", plan)
    write_json(directory / "labels.json", labels)
    manifest = {"P2_RERANK_PREP": "READY", "realModelCalls": 0, "databaseWrites": 0,
                "inputs": provenance, "planSha256": digest(encode(plan)), "labelsSha256": digest(encode(labels)),
                "workspaceStatus": "INTERACTIVE_INPUT_REQUIRED; PERMISSION_UNVERIFIED",
                "modelAccessStatus": "NOT_VERIFIED_WITH_PAID_REQUEST", "provenance": "10 HUMAN_ORIGINAL + 70 AI_SUGGESTION; PROVISIONAL"}
    write_json(directory / "manifest.json", manifest)
    render_report(directory, dataset, labels, plan)
    return manifest


def verify_prepared(root):
    directory = root / "target/rag-p2/prepared"
    manifest = read_json(directory / "manifest.json")
    for name, source in manifest["inputs"].items():
        require(source["sha256"] == FROZEN[name], "Manifest input binding changed")
        # If an original still exists, verify it too; snapshots support later offline review.
        original = Path(source["originalPath"])
        if original.exists():
            require(digest(original.read_bytes()) == FROZEN[name], "Original evidence changed after preparation")
    dataset, labels = load_dataset(root / "target/rag-p2/inputs")
    plan = make_plan(dataset)
    require((directory / "requests.json").read_bytes() == encode(plan), "Prepared request plan changed")
    require((directory / "labels.json").read_bytes() == encode(labels), "Prepared labels changed")
    require(manifest["planSha256"] == digest(encode(plan)) and manifest["labelsSha256"] == digest(encode(labels)),
            "Prepared manifest SHA mismatch")
    return dataset, labels, plan


def verify_offline_gate(root):
    evidence = read_json(root / "target/rag-p2/offline/test-results.json")
    require(evidence.get("passed") is True and evidence.get("networkAttempts") == 0,
            "Offline tests must pass before paid execution")
    for name, expected in evidence["sourceSha256"].items():
        require(digest((root / "tools/rag-p2" / name).read_bytes()) == expected,
                "P2 source changed after offline tests; run Offline again")
    powershell = read_json(root / "target/rag-p2/offline/powershell-tests.json")
    require(powershell.get("passed") is True and powershell["runScriptSha256"] ==
            digest((root / "tools/rag-p2/run.ps1").read_bytes()), "PowerShell entry gate not verified")


def verify_saved_run(root, dataset, plan):
    directory = root / "target/rag-p2/run-once"
    raw_result = (directory / "results.json").read_bytes()
    require(digest(raw_result) == (directory / "results.sha256").read_text("ascii").strip(), "Saved run SHA changed")
    result = json.loads(raw_result)
    require(result["planSha256"] == digest(encode(plan)) and result["inputSha256"] == FROZEN, "Saved run input binding")
    require(result["model"] == MODEL and 0 <= result["httpAttemptsReserved"] <= 16, "Saved run model/count gate")
    require(len(result["queries"]) == 16, "Saved run query count")
    for q, request, record in zip(dataset, plan["requests"], result["queries"], strict=True):
        require(q["queryId"] == record["queryId"] and q["questionText"] == record["rerankQueryText"], "Saved run query mismatch")
        if record["status"] != "COMPLETE":
            continue
        require((directory / f"{q['queryId']}.request.json").read_bytes() == encode(request["body"]), "Saved request changed")
        raw = (directory / f"{q['queryId']}.response.json").read_bytes()
        require(not record["responseRedacted"] and digest(raw) == record["responseSha256"], "Saved successful response changed")
        mapped, usage, request_id = validate_response(raw, q)
        require(mapped == record["rerankResults"] and usage == record["usage"] and request_id == record["requestId"],
                "Saved mapping/usage mismatch")
    if result["P2_RERANK_RESULT"] == "PASS":
        require(result["origin"] == "REAL_RERANK" and result["httpAttemptsReserved"] == result["httpSuccesses"] == 16 and
                all(q["status"] == "COMPLETE" for q in result["queries"]), "Incomplete run cannot claim PASS")
    recalculated = json.loads(raw_result)
    refresh_cost(recalculated)
    require(result["cost"] == recalculated["cost"], "Saved cost/latency accounting mismatch")
    return result


def refresh_cost(result):
    complete = [q for q in result["queries"] if q["status"] == "COMPLETE"]
    prompt = sum(q["usage"]["prompt_tokens"] for q in complete)
    total = sum(q["usage"]["total_tokens"] for q in complete)
    latencies = [q["elapsedMs"] for q in complete]
    unknown = any(q["status"] in ("INTENT_RESERVED", "FAILED") for q in result["queries"])
    result["cost"] = {"promptTokensKnown": prompt, "totalTokensKnown": total,
                      "estimatedGrossCostCnyKnown": str(PRICE * Decimal(prompt) / Decimal(1000000)),
                      "actualInvoiceCny": None, "billingComplete": not unknown and len(complete) == 16,
                      "unaccountedAttemptPossible": unknown, "completedUsageCount": len(complete),
                      "latencyMeanMs": statistics.mean(latencies) if latencies else None,
                      "latencyMedianMs": statistics.median(latencies) if latencies else None,
                      "latencyP95NearestRankMs": sorted(latencies)[math.ceil(.95 * len(latencies)) - 1] if latencies else None,
                      "latencySumMs": sum(latencies)}


def execute(root, credentials, transport=post_once, origin="REAL_RERANK"):
    dataset, labels, plan = verify_prepared(root)
    if origin == "REAL_RERANK":
        verify_offline_gate(root)
    require(credentials.get("authorization") == AUTHORIZATION, "Missing one-time human authorization")
    require(credentials.get("planSha256") == digest(encode(plan)), "Authorization plan binding mismatch")
    url = endpoint(credentials.get("workspaceId"))
    api_key = credentials.get("apiKey")
    require(isinstance(api_key, str) and api_key.strip() and not re.search(r"[\s\x00-\x1f\x7f]", api_key), "Missing/invalid API Key")
    directory = root / "target/rag-p2/run-once"
    # Atomic reservation is outside the request loop. No --force, resume, or retry.
    directory.mkdir(exist_ok=False)
    result = {"P2_RERANK_RESULT": "IN_PROGRESS", "origin": origin, "model": MODEL, "endpoint": url,
              "startedUtc": utc(), "planSha256": digest(encode(plan)), "inputSha256": dict(FROZEN),
              "httpAttemptsReserved": 0, "httpSuccesses": 0, "retryCount": 0,
              "otherModelCalls": 0, "databaseConnections": 0, "databaseWrites": 0, "vectorWrites": 0,
              "qualityConclusion": "PROVISIONAL; NOT INDEPENDENTLY GENERALIZED",
              "queries": [{"queryId": q["queryId"], "status": "NOT_STARTED", "rerankQueryText": q["questionText"]}
                          for q in dataset]}
    refresh_cost(result)
    write_json(directory / "results.json", result)
    failure = None
    try:
        for q, request, record in zip(dataset, plan["requests"], result["queries"], strict=True):
            require(result["httpAttemptsReserved"] < MAX_CALLS, "16-attempt limit")
            known_cost = Decimal(result["cost"]["estimatedGrossCostCnyKnown"])
            remaining_proxy = sum(r["inputTokenByteProxy"] for r in plan["requests"][result["httpAttemptsReserved"]:])
            require(known_cost + PRICE * Decimal(remaining_proxy) / Decimal(1000000) <= BUDGET, "Runtime budget reference gate")
            record.update(status="INTENT_RESERVED", attempt=1, startedUtc=utc(), requestSha256=request["requestSha256"])
            result["httpAttemptsReserved"] += 1
            write_json(directory / f"{q['queryId']}.request.json", request["body"])
            refresh_cost(result)
            write_json(directory / "results.json", result)
            started = time.perf_counter()
            try:
                status, raw = transport(url, api_key, encode(request["body"]))
                record.update(elapsedMs=round((time.perf_counter() - started) * 1000, 3), httpStatus=status,
                              responseSha256=digest(raw))
                # Preserve raw successful/error body unless it echoes credentials; never log headers.
                sanitized = raw.replace(api_key.encode("utf-8"), b"[REDACTED]")
                record["responseRedacted"] = sanitized != raw
                (directory / f"{q['queryId']}.response.json").write_bytes(sanitized)
                require(status == 200, f"HTTP_{status}; permission/model/network failure, no fallback or retry")
                mapped, usage, request_id = validate_response(raw, q)
                record.update(status="COMPLETE", endedUtc=utc(), rerankResults=mapped, usage=usage, requestId=request_id)
                result["httpSuccesses"] += 1
            except BaseException as exc:
                record.update(status="FAILED", endedUtc=utc(), elapsedMs=round((time.perf_counter() - started) * 1000, 3),
                              failureType=type(exc).__name__,
                              failureReason=str(exc) if isinstance(exc, GateError) else "Transport/interruption failure; outcome may be charged; no retry")
                raise
            finally:
                refresh_cost(result)
                write_json(directory / "results.json", result)
        require(Decimal(result["cost"]["estimatedGrossCostCnyKnown"]) <= BUDGET, "Final known cost exceeds budget reference")
        result["P2_RERANK_RESULT"] = "PASS" if origin == "REAL_RERANK" else "OFFLINE_MOCK_COMPLETE"
    except BaseException as exc:
        result["P2_RERANK_RESULT"] = "FAIL" if origin == "REAL_RERANK" else "OFFLINE_MOCK_FAILED"
        result["blockedReason"] = str(exc) if isinstance(exc, GateError) else type(exc).__name__
        failure = exc
    finally:
        result["endedUtc"] = utc()
        refresh_cost(result)
        write_json(directory / "results.json", result)
        (directory / "results.sha256").write_text(digest((directory / "results.json").read_bytes()) + "\n", encoding="ascii")
        render_report(directory, dataset, labels, plan, result)
    if failure:
        raise GateError("Run stopped; preserve run-once, inspect results.json; no automatic retry") from failure
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["prepare", "verify", "execute-once", "analyze"])
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--downloads", type=Path, default=Path.home() / "Downloads")
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        if args.mode == "prepare":
            prepare(root, args.downloads.resolve())
            print("P2_RERANK_PREP=READY; actual HTTP=0, database/vector writes=0")
        elif args.mode == "verify":
            _, _, plan = verify_prepared(root)
            print(f"P2_RERANK_PREP=READY; calls<=16; cost_proxy_cny={plan['conservativeCostProxyCny']}; budget_reference_cny={BUDGET}")
        elif args.mode == "analyze":
            dataset, labels, plan = verify_prepared(root)
            directory = root / "target/rag-p2/run-once"
            render_report(directory, dataset, labels, plan, verify_saved_run(root, dataset, plan))
            print("Offline analysis regenerated; HTTP=0")
        else:
            # Credentials/consent sent by interactive PowerShell via private child stdin, never argv/file/env.
            credentials = json.loads(sys.stdin.read(65536))
            result = execute(root, credentials)
            print(f"P2_RERANK_RESULT={result['P2_RERANK_RESULT']}; http_attempts={result['httpAttemptsReserved']}")
    except (GateError, OSError, KeyError, json.JSONDecodeError) as exc:
        # Do not print exception repr/tracebacks that might contain credentials or provider input.
        message = str(exc) if isinstance(exc, GateError) else type(exc).__name__
        status = "P2_RERANK_RESULT=FAIL" if args.mode == "execute-once" and (root / "target/rag-p2/run-once").exists() else "P2_RERANK_PREP=BLOCKED"
        print(f"{status}; {message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
