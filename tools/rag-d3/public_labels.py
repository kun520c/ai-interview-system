"""Reconcile offline AI judgments and score the public D3-A retrieval run."""

import argparse
from collections import Counter
from hashlib import sha256
import json
import math
from pathlib import Path
from statistics import median


ROOT = Path(__file__).resolve().parents[2]
PUBLIC = ROOT / "docs/verification/D3A_RETRIEVAL_QUERIES_V1_20261114.json"
MANIFEST = ROOT / "docs/rag/d3-b/CORPUS_MANIFEST_20261114.json"
LABEL_DIR = ROOT / "docs/rag/d3-d"
ASSESSORS = tuple(LABEL_DIR / f"public-assessor-{name}.json" for name in ("a", "b"))
FROZEN_LABELS = LABEL_DIR / "PUBLIC_AI_LABELS_20261114.json"
KINDS = ("DIRECT", "RELATED_ONLY", "IRRELEVANT", "UNCERTAIN")
FIELDS = {"DIRECT": "direct", "RELATED_ONLY": "relatedOnly", "UNCERTAIN": "uncertain"}
STAGES = {"A": "raw", "B": "hydrated", "C": "prompt"}

# Rechecked against the two frozen judgments and rank-free production text.
# A rank-free consensus review cannot supply the independence of a third reviewer.
DECISIONS = {
    ("Q02", "J04/1"): ("RELATED_ONLY", (), "稳定哈希键有助于实现查找，未提供按到达顺序展示的容器合同。"),
    ("Q04", "J15/1"): ("DIRECT", (), "同键计数 read-modify-write 的反例及 merge 原子性独立支持自增子意图。"),
    ("Q07", "D13/1"): ("RELATED_ONLY", (), "只建议看两类统计，未给区分 TTL 和内存压力的解释或具体计数。"),
    ("Q09", "D08/1"): ("RELATED_ONLY", (), "预约幂等说明一般恢复机制，未涉及面试项目的会话、题目与正文合同。"),
    ("Q09", "D16/1"): ("RELATED_ONLY", (), "HTTP 创建合同缺少面试提交的归属与精确答文检查。"),
    ("Q14", "J09/1"): ("DIRECT", (), "有效 ListIterator 下就地删当前项，可独立支持安全注销策略的一个子项。"),
    ("Q16", "J20/1"): ("RELATED_ONLY", (), "限时锁如何传播中断与等待唤醒的标志清理相邻，缺少清理前提。"),
    ("Q24", "D03/1"): ("DIRECT", (), "低选择性时优化器可能取更低成本的其他路径，直接支持有索引仍大量扫描的一项机制。"),
    ("Q24", "D04/1"): ("DIRECT", (), "缺中间列时无法用单一连续范围跳过各组旧记录，独立解释候选过多的一种原因。"),
    ("Q25", "J15/1"): ("DIRECT", ("Q25-I2",), "写时复制证明浅层引用共享；题设第三方列表活视图和结构快照的对照由 J13 给出。"),
    ("Q28", "J23/1"): ("DIRECT", ("Q28-I1",), "底层停止条件明确；按合同重试是提醒，缺可执行的幂等判定。"),
    ("Q30", "D06/1"): ("RELATED_ONLY", (), "旧读快照是上游背景，未说明读回填与写失效并发交错。"),
    ("Q31", "S10/1"): ("RELATED_ONLY", (), "版本条件有类比价值，缺 Redis 租约的授权顺序与资源端 fencing 前提。"),
    ("Q32", "D16/1"): ("DIRECT", ("Q32-I1",), "已发送而没收到响应不足以推断远端是否执行；该传输侧推理适用于批次。"),
    ("Q32", "S08/1"): ("RELATED_ONLY", (), "远端已执行但发送方未确认的类比来自持久通知流程，未解释 Redis 批次执行。"),
    ("Q34", "J14/1"): ("DIRECT", ("Q34-I2",), "并发 Map 的 get/put 仍可能丢更新，直接反驳复合更新自然原子的推断。"),
    ("Q34", "J15/1"): ("DIRECT", ("Q34-I2",), "单键 merge 与 compute 的机制完整；提及发布不可变整体未给安全发布保证。"),
    ("Q35", "D07/1"): ("DIRECT", ("Q35-I1", "Q35-I2"), "断连结果未知并要求稳定键、载荷校验、唯一约束；未给 API 重放响应合同。"),
    ("Q37", "J05/1"): ("RELATED_ONLY", (), "不变性与捕获是背景，未直接解释参数化 instanceof 的可检查转换。"),
}


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def digest(path):
    return sha256(path.read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def write(path, obj):
    with path.open("x", encoding="utf-8") as stream:
        json.dump(obj, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")


def catalog():
    queries = {query["id"]: query for query in read(PUBLIC)["queries"]}
    chunks = {
        f"{doc['id']}/{chunk['index']}": chunk["sha256"]
        for doc in read(MANIFEST)["documents"]
        for chunk in doc["chunks"]
    }
    require(len(queries) == 60, "public query count")
    require(len(chunks) == 52, "public frozen chunk count")
    return queries, chunks


def flatten(assessor, queries, chunks):
    require(assessor["querySha256"] == digest(PUBLIC), "assessor query hash")
    manifest_hash = assessor.get("manifestSha256", assessor.get("corpusManifestSha256"))
    require(manifest_hash == digest(MANIFEST), "assessor corpus hash")
    require(assessor["chunks"] == chunks, "assessor chunk hash catalog")
    require(assessor["defaultLabel"] == "IRRELEVANT", "assessor default")
    flat = {}
    ids = set()
    for row in assessor["queries"]:
        query_id = row["queryId"]
        require(query_id in queries and query_id not in ids, f"assessor query {query_id}")
        ids.add(query_id)
        for kind, field in FIELDS.items():
            for evidence in row[field]:
                key = (query_id, evidence["chunkKey"])
                require(key not in flat and key[1] in chunks, f"assessor identity {key}")
                flat[key] = {"label": kind, **evidence}
    require(ids == queries.keys(), "assessor query completeness")
    return flat


def utf16_length(value):
    return len(value.encode("utf-16-le")) // 2


def verify_spans(rows, chunk_texts, chunks):
    require(chunk_texts.keys() == chunks.keys(), "text dump chunk catalog")
    for key, text in chunk_texts.items():
        require(sha256(text.encode()).hexdigest() == chunks[key], f"text dump digest {key}")
    for (_, key), evidence in rows.items():
        for span in evidence.get("spans", []):
            text = chunk_texts[key].encode("utf-16-le")
            start, end = span["spanStartUtf16"], span["spanEndUtf16"]
            require(0 <= start < end <= len(text) // 2, f"bad span bounds {key}")
            require(
                text[start * 2:end * 2].decode("utf-16-le") == span["quote"]
                and utf16_length(span["quote"]) == end - start,
                f"span digest mismatch {key}",
            )


def disagreement(key, left, right, required):
    left_kind, right_kind = left["label"], right["label"]
    left_intents = set(left.get("supportedIntentIds", [])) & required
    right_intents = set(right.get("supportedIntentIds", [])) & required
    if left_kind == right_kind and left_intents == right_intents:
        return left_kind, sorted(left_intents), None
    if key in DECISIONS:
        kind, intents, reason = DECISIONS[key]
        return kind, list(intents), reason
    if {left_kind, right_kind} == {"RELATED_ONLY", "IRRELEVANT"}:
        related = left if left_kind == "RELATED_ONLY" else right
        return "RELATED_ONLY", [], f"保留为诊断背景，计分仍为零。{related['reason']}"
    return "UNCERTAIN", [], "尚无共同可复核的证据判断，待人工裁决。"


def freeze(chunk_texts):
    queries, chunks = catalog()
    sources = [read(path) for path in ASSESSORS]
    assessments = [flatten(source, queries, chunks) for source in sources]
    for rows in assessments:
        verify_spans(rows, chunk_texts, chunks)
    matrix = {kind: dict.fromkeys(KINDS, 0) for kind in KINDS}
    defaults = {"label": "IRRELEVANT"}
    differences = []
    rows = {}
    counts = Counter()
    for query_id, query in queries.items():
        rows[query_id] = {}
        required = {intent["id"] for intent in query.get("requiredIntents", [])}
        for chunk_key in chunks:
            key = (query_id, chunk_key)
            left, right = (assessment.get(key, defaults) for assessment in assessments)
            matrix[left["label"]][right["label"]] += 1
            kind, intents, decision = disagreement(key, left, right, required)
            counts[kind] += 1
            if decision:
                differences.append({
                    "queryId": query_id, "chunkKey": chunk_key,
                    "a": left, "b": right, "finalLabel": kind,
                    "supportedIntentIds": intents, "reason": decision,
                })
            if kind != "IRRELEVANT":
                source = left if left["label"] == kind else right
                row = {
                    "label": kind,
                    "supportedIntentIds": intents or ([f"{query_id}-I1"] if kind == "DIRECT" and not required else []),
                    "harmful": left.get("harmful", False) or right.get("harmful", False),
                    "reason": decision or source.get("reason"),
                }
                if kind == "DIRECT":
                    spans = source["spans"]
                    require(spans and (not required or intents), f"direct evidence {key}")
                    row["spans"] = spans
                    row["versionCondition"] = source["versionCondition"]
                rows[query_id][chunk_key] = row
    total = len(queries) * len(chunks)
    same = sum(matrix[kind][kind] for kind in KINDS)
    expected = sum(
        sum(matrix[kind].values()) * sum(row[kind] for row in matrix.values()) for kind in KINDS
    ) / total**2
    return {
        "version": "D3-D-PUBLIC-AI-20261114",
        "status": "AI_TEMPORARY_LABELS",
        "adjudicator": "AI-A-author_system，依据两份独立原始判断复核分歧，未另设第三位或人工裁决者",
        "querySha256": digest(PUBLIC),
        "corpusManifestSha256": digest(MANIFEST),
        "sources": [
            {"path": str(path.relative_to(ROOT)), "sha256": digest(path), "assessorId": source["assessorId"]}
            for path, source in zip(ASSESSORS, sources)
        ],
        "chunks": chunks,
        "defaultLabel": "IRRELEVANT",
        "pairCount": total,
        "counts": dict(counts),
        "agreement": {
            "matrixRowsAColumnsB": matrix, "labelDisagreements": total - same,
            "rawAgreement": same / total, "cohenKappa": (same / total - expected) / (1 - expected),
            "intentOnlyDisagreements": sum(entry["a"]["label"] == entry["b"]["label"] for entry in differences),
        },
        "decisions": differences,
        "evidenceGaps": {
            row["queryId"]: row["evidenceGap"] for row in sources[0]["queries"] if row.get("evidenceGap")
        },
        "queries": rows,
    }


def load_run(folder, queries, chunks):
    require(folder.is_dir(), "prediction directory missing")
    inputs = {path.stem: path for path in folder.glob("*.json")}
    require(inputs.keys() <= queries.keys(), f"unknown query IDs {sorted(inputs.keys() - queries.keys())}")
    successful = {}
    failures = {}
    for query_id in queries:
        if query_id not in inputs:
            failures[query_id] = "缺少持久化预测记录，原因以运行日志为准"
            continue
        data = read(inputs[query_id])
        require(data["id"] == query_id, f"query identity {query_id}")
        by_stage = {}
        for stage, field in STAGES.items():
            by_stage[stage] = {}
            for row in data[field]:
                key, rank = row["key"], row["rank"]
                require(key in chunks, f"unknown chunk {query_id} {stage} {key}")
                require(type(rank) is int and 1 <= rank <= 5, f"invalid rank {query_id} {stage}")
                require(rank not in by_stage[stage], f"duplicate rank {query_id} {stage}")
                require(not by_stage[stage] or rank > max(by_stage[stage]), f"reordered rank {query_id} {stage}")
                by_stage[stage][rank] = key
                if stage == "A":
                    require(rank == len(by_stage[stage]), f"raw rank gap {query_id}")
                    require(math.isfinite(row["score"]), f"nonfinite score {query_id}")
                else:
                    require(row["sha256"] == chunks[key], f"content hash {query_id} {stage} {key}")
                    earlier = by_stage["A" if stage == "B" else "B"]
                    require(earlier.get(rank) == key, f"rank identity lost {query_id} {stage}")
        require(type(data["elapsedMs"]) is int and data["elapsedMs"] >= 0, f"invalid elapsedMs {query_id}")
        successful[query_id] = data
    return successful, failures


def judged_rows(prediction, field, labels):
    seen = set()
    result = []
    for item in prediction.get(field, []):
        row = labels.get(item["key"], {"label": "IRRELEVANT", "harmful": False})
        label = row["label"]
        duplicate = item["key"] in seen
        seen.add(item["key"])
        result.append({
            "rank": item["rank"], "key": item["key"], "label": label, "duplicate": duplicate,
            "isDirect": label == "DIRECT" and not duplicate,
            "harmful": row["harmful"],
            "supportedIntentIds": row.get("supportedIntentIds", []) if not duplicate else [],
            **({"score": item["score"]} if "score" in item else {}),
        })
    return result


def summarize(query_ids, details, stage):
    selected = [details[query_id]["stages"][stage] for query_id in query_ids]
    first_ranks = [next((row["rank"] for row in rows if row["isDirect"]), 0) for rows in selected]
    total_rows = sum(map(len, selected))
    directs = sum(row["isDirect"] for rows in selected for row in rows)
    denominator = len(query_ids)
    return {
        "queries": denominator,
        "hit": {str(k): sum(0 < rank <= k for rank in first_ranks) for k in (1, 3, 5)},
        "hitRate": {str(k): sum(0 < rank <= k for rank in first_ranks) / denominator for k in (1, 3, 5)},
        "mrrAt5": sum(1 / rank for rank in first_ranks if rank) / denominator,
        "firstDirectRank": {str(rank) if rank else "miss": first_ranks.count(rank) for rank in range(6)},
        "precisionAt5": directs / (5 * denominator),
        "precisionReturned": directs / total_rows if total_rows else None,
        "returned": total_rows,
        "direct": directs,
        "duplicateChunks": sum(row["duplicate"] for rows in selected for row in rows),
        "empty": sum(not rows for rows in selected),
    }


def score(folder, labels_path):
    queries, chunks = catalog()
    labels = read(labels_path)
    require(labels["querySha256"] == digest(PUBLIC), "label query hash")
    require(labels["corpusManifestSha256"] == digest(MANIFEST), "label corpus hash")
    require(labels["chunks"] == chunks and labels["queries"].keys() == queries.keys(), "labels catalog")
    require(labels["defaultLabel"] == "IRRELEVANT", "labels default")
    require(labels["counts"].get("UNCERTAIN", 0) == 0, "pending evidence adjudication")
    predictions, failures = load_run(folder, queries, chunks)
    details = {}
    groups = {}
    intent_coverage = {stage: Counter() for stage in STAGES}
    attrition = {transition: Counter() for transition in ("A_to_B", "B_to_C")}
    for query_id, query in queries.items():
        prediction = predictions.get(query_id, {})
        rows = {stage: judged_rows(prediction, field, labels["queries"][query_id]) for stage, field in STAGES.items()}
        required = {entry["id"] for entry in query.get("requiredIntents", [])}
        covered = {
            stage: sorted({
                intent for row in entries if row["isDirect"] for intent in row["supportedIntentIds"]
            } & required)
            for stage, entries in rows.items()
        }
        lost = {}
        for from_stage, to_stage in (("A", "B"), ("B", "C")):
            transition = f"{from_stage}_to_{to_stage}"
            kept = {row["rank"] for row in rows[to_stage]}
            lost[transition] = [
                {**row, "reason": "未通过检索资格过滤，逐条内部原因未记录"
                 if to_stage == "B" else "未被实际 Prompt 证据序列化"}
                for row in rows[from_stage] if row["rank"] not in kept
            ]
            attrition[transition].update(row["label"] for row in lost[transition])
        if required:
            for stage, supported in covered.items():
                intent_coverage[stage]["supported"] += len(supported)
                intent_coverage[stage]["required"] += len(required)
                intent_coverage[stage]["fullAnswers"] += set(query["fullAnswerRequires"]) <= set(supported)
                intent_coverage[stage]["queries"] += 1
        details[query_id] = {
            "stages": rows, "lost": lost, "intents": covered,
            "predictionSha256": digest(folder / f"{query_id}.json") if prediction else None,
            "promptFirstDirectDisplayIndex": next((i for i, row in enumerate(rows["C"], 1) if row["isDirect"]), None),
            "elapsedMs": prediction.get("elapsedMs"), "embeddingUsage": prediction.get("embeddingUsage"),
            "valid": query_id in predictions,
        }
        for group in [f"类型/{query['type']}", *(f"领域/{domain}" for domain in query["domains"])]:
            groups.setdefault(group, []).append(query_id)
    positives = [key for key, query in queries.items() if query["expectedEvidence"] == "DIRECT_EXPECTED"]
    negatives = [key for key in queries if key not in positives]
    require(len(positives) == 48 and len(negatives) == 12, "expected group counts")
    nonempty_negative = sum(bool(details[key]["stages"]["C"]) for key in negatives)
    valid_negative = sum(details[key]["valid"] for key in negatives)
    negative_rows = [row for key in negatives for row in details[key]["stages"]["C"]]
    harmful_queries = [key for key in queries if any(row["harmful"] for row in details[key]["stages"]["C"])]
    positive_metrics = {stage: summarize(positives, details, stage) for stage in STAGES}
    all_metrics = {stage: summarize(list(queries), details, stage) for stage in STAGES}
    negative_truth_conflicts = [
        key for key in negatives if any(row["label"] == "DIRECT" for row in labels["queries"][key].values())
    ]
    b, c = positive_metrics["B"], positive_metrics["C"]
    conditions = {
        "validQueries60": len(predictions) == 60,
        "harmfulPrompt0": not harmful_queries,
        "negativeTruthConflict0": not negative_truth_conflicts,
        "B_hit5_at_least_43": b["hit"]["5"] >= 43,
        "B_hit1_at_least_34": b["hit"]["1"] >= 34,
        "B_mrrAt5_at_least_075": b["mrrAt5"] >= .75,
        "C_positive_hit5_at_least_43": c["hit"]["5"] >= 43,
        "C_precisionReturned_at_least_065": all_metrics["C"]["precisionReturned"] is not None
        and all_metrics["C"]["precisionReturned"] >= .65,
    }
    timings = sorted(record["elapsedMs"] for record in predictions.values())
    positive_misses = [
        key for key in positives if not any(row["isDirect"] for row in details[key]["stages"]["C"])
    ]
    return {
        "set": "public D3-A 60",
        "evidenceLevel": "AI 临时标签。公开集用于冻结回归，正式人工真值待复核。",
        "status": "AI_THRESHOLDS_MET" if all(conditions.values()) else "THRESHOLDS_NOT_MET",
        "conditions": conditions,
        "publicLabelSha256": digest(labels_path),
        "valid": len(predictions),
        "failures": failures,
        "positive": positive_metrics,
        "allIncludingExpectedNegatives": all_metrics,
        "groups": {
            group: {
                "sampleSize": len(ids), "positiveSize": len(pos := [key for key in ids if key in positives]),
                "positive": {stage: summarize(pos, details, stage) for stage in STAGES} if pos else None,
                "all": {stage: summarize(ids, details, stage) for stage in STAGES},
            }
            for group, ids in sorted(groups.items())
        },
        "negative": {
            "valid": valid_negative, "expected": len(negatives), "nonemptyPrompt": nonempty_negative,
            "nonemptyPromptRateValid": nonempty_negative / valid_negative if valid_negative else None,
            "promptEvidenceLabels": dict(Counter(row["label"] for row in negative_rows)),
            "harmfulQueries": [key for key in harmful_queries if key in negatives],
            "truthConflicts": negative_truth_conflicts,
        },
        "intentCoverage": {stage: dict(counts) for stage, counts in intent_coverage.items()},
        "attrition": {transition: dict(counts) for transition, counts in attrition.items()},
        "harmfulQueries": harmful_queries,
        "positivePromptMisses": positive_misses,
        "elapsedMs": {
            "min": timings[0], "median": median(timings), "p95NearestRank": timings[math.ceil(.95 * len(timings)) - 1],
            "max": timings[-1],
        } if timings else None,
        "embeddingUsage": {
            "tokens": sum(value for item in predictions.values() if type(value := item.get("embeddingUsage")) is int),
            "missingUsage": sum(type(item.get("embeddingUsage")) is not int for item in predictions.values()),
        },
        "queries": details,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    freeze_command = commands.add_parser("freeze")
    freeze_command.add_argument("chunk_texts", type=Path, help="production Chunk JSON map, key to UTF-8 text")
    freeze_command.add_argument("output", type=Path)
    score_command = commands.add_parser("score")
    score_command.add_argument("predictions", type=Path)
    score_command.add_argument("output", type=Path)
    score_command.add_argument("--labels", type=Path, default=FROZEN_LABELS)
    args = parser.parse_args()
    result = freeze(read(args.chunk_texts)) if args.command == "freeze" else score(args.predictions, args.labels)
    write(args.output, result)
    print(args.output, result["status"])


if __name__ == "__main__":
    main()
