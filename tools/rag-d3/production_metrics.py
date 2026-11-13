#!/usr/bin/env python3
"""Check the frozen production score-point labels and summarize genuine retrieval predictions."""

import argparse
from collections import Counter, defaultdict
import hashlib
import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
LABELS = ROOT / "docs/rag/d3-d/PRODUCTION_POINT_LABELS_20261114.json"
INPUTS = ROOT / "docs/rag/d3-d/PRODUCTION_QUERY_INPUTS_20261114.json"
CORPUS = ROOT / "docs/rag/d3-b/CORPUS_MANIFEST_20261114.json"
BANK = ROOT / "docs/question-bank/D1_QUESTION_BANK_V2_20261114.json"
IMPORT = ROOT / "docs/question-bank/D1_EVALUATION_IMPORT_MANIFEST_20261114.json"
STAGES = ("raw", "hydrated", "prompt")
TOP_K = 5


def require(condition, description):
    if not condition:
        raise ValueError(description)


def read_json(path, expected_hash=None):
    raw = path.read_bytes()
    if expected_hash is not None:
        require(hashlib.sha256(raw).hexdigest() == expected_hash, f"SHA-256 mismatch for {path.name}")
    return json.loads(raw)


def frozen_inputs(chunks_path):
    labels = read_json(LABELS)
    manifest = read_json(CORPUS, labels["corpusManifestSha256"])
    bank = read_json(BANK, labels["bankSha256"])["questions"]
    db_map = read_json(IMPORT, labels["pointMapSha256"])["databaseIdMapping"]
    inputs = read_json(INPUTS, labels["queryInputsSha256"])
    require(inputs["bankSha256"] == labels["bankSha256"] and inputs["importSha256"] == labels["pointMapSha256"],
            "Query inputs and point labels have different question-bank identities")
    chunks = {f"{doc['id']}/{chunk['index']}": chunk["sha256"]
              for doc in manifest["documents"] for chunk in doc["chunks"]}
    require(len(chunks) == 52, "Unexpected chunk count")
    if chunks_path:
        validate_spans(labels["points"], chunks, read_json(chunks_path))

    db_ids = {q["bankId"]: q["pointIds"] for q in db_map}
    require(len(bank) == len(db_ids) == 63, "Unexpected question count")
    expected = {}
    for question in bank:
        bank_id = question["bankId"]
        scoring_points = question["scoringPoints"]
        require(len(scoring_points) == len(db_ids[bank_id]), f"D1 database point count differs for {bank_id}")
        for index, (point, db_id) in enumerate(zip(scoring_points, db_ids[bank_id]), 1):
            expected[f"{bank_id}.{index:02d}"] = (bank_id, db_id, point["pointType"], point["weight"])

    points_by_question = defaultdict(list)
    seen = set()
    for point in labels["points"]:
        point_id = point["pointId"]
        require(point_id not in seen and point_id in expected, f"Unexpected point: {point_id}")
        seen.add(point_id)
        actual = (point["bankId"], point["databasePointId"], point["type"], point["weight"])
        require(actual == expected[point_id], f"D1 metadata mismatch for {point_id}")
        require(set(point["evidence"]) <= chunks.keys(), f"Unknown evidence chunk for {point_id}")
        require(any(item["role"] == "DIRECT" for item in point["evidence"].values()),
                f"No direct point evidence in frozen corpus for {point_id}")
        for evidence in point["evidence"].values():
            require(evidence["role"] in {"DIRECT", "CONTRIBUTION"}, f"Invalid point evidence role for {point_id}")
            if point["type"] == "CORE" and evidence["role"] == "DIRECT":
                require(bool(evidence["spans"]), f"CORE evidence requires an excerpt for {point_id}")
        points_by_question[point["bankId"]].append(point)
    require(len(seen) == len(expected) == 298, "Point inventory does not cover D1")
    require(sum(p["type"] == "CORE" for p in labels["points"]) == 77, "Unexpected CORE point count")
    return inputs["production"], points_by_question, chunks


def validate_spans(points, hashes, texts):
    require(set(hashes) == set(texts), "Chunk text export does not match the manifest")
    for key, text in texts.items():
        require(hashlib.sha256(text.encode()).hexdigest() == hashes[key], f"Chunk bytes differ for {key}")
    for point in points:
        for key, evidence in point["evidence"].items():
            text = texts[key].encode("utf-16-le")
            for span in evidence["spans"]:
                start, end = span["spanStartUtf16"], span["spanEndUtf16"]
                require(0 <= start < end <= len(text) // 2, f"Invalid span for {point['pointId']} in {key}")
                require(text[2 * start:2 * end].decode("utf-16-le") == span["quote"],
                        f"Excerpt mismatch for {point['pointId']} in {key}")


def assigned_points(queries, points_by_question):
    seen_main = set()
    seen_followup_categories = set()
    assignments = {}
    for query in queries:
        query_id = query["id"]
        if query["type"] == "MAIN":
            require(query_id not in seen_main and query_id in points_by_question, f"Unexpected MAIN: {query_id}")
            seen_main.add(query_id)
            assignments[query_id] = points_by_question[query_id]
        elif query["type"] == "FOLLOW_UP":
            bank_id = query_id.removesuffix("-F")
            require(bank_id in seen_main and query_id.endswith("-F")
                    and query["category"] not in seen_followup_categories, f"Unexpected FOLLOW_UP: {query_id}")
            seen_followup_categories.add(query["category"])
            target = next(p for p in points_by_question[bank_id] if p["type"] == "CORE")
            assignments[query_id] = [target]
        else:
            raise ValueError(f"Unknown query type for {query_id}")
    require(len(seen_main) == 63 and len(seen_followup_categories) == 9 and len(queries) == 72,
            "Expected 63 MAIN and 9 FOLLOW_UP queries")
    return assignments


def check_prediction(record, query, hashes):
    for key in ("id", "type", "category", "difficulty"):
        require(record[key] == query[key], f"Prediction metadata differs for {query['id']}: {key}")
    previous = None
    for stage in STAGES:
        rows = record[stage]
        ranks = [row["rank"] for row in rows]
        require(all(isinstance(rank, int) and 1 <= rank <= TOP_K for rank in ranks)
                and sorted(set(ranks)) == ranks, f"Invalid ranks for {query['id']} {stage}")
        current = {row["rank"]: row["key"] for row in rows}
        if previous is None:
            require(ranks == list(range(1, len(ranks) + 1)), f"Missing raw rank for {query['id']}")
        else:
            require(all(previous.get(rank) == key for rank, key in current.items()),
                    f"Unexpected evidence change or reordering for {query['id']} {stage}")
        for row in rows:
            require(row["key"] in hashes, f"Unknown chunk for {query['id']} {stage}: {row['key']}")
            if stage == "raw":
                require(math.isfinite(row["score"]), f"Nonfinite raw similarity for {query['id']}")
            else:
                require(row["sha256"] == hashes[row["key"]], f"Chunk hash differs for {query['id']} {stage}")
        previous = current
    elapsed = record["elapsedMs"]
    require(isinstance(elapsed, int) and elapsed >= 0, f"Invalid elapsed time for {query['id']}")


def score_row(record, query, points):
    stages = {stage: [] for stage in STAGES}
    if record is not None:
        stages = {stage: record[stage] for stage in STAGES}
    points_by_chunk = defaultdict(list)
    helpers_by_chunk = defaultdict(list)
    for point in points:
        for key, evidence in point["evidence"].items():
            target = points_by_chunk if evidence["role"] == "DIRECT" else helpers_by_chunk
            target[key].append(point["pointId"])
    judged = {}
    for stage, rows in stages.items():
        seen = set()
        assessed = []
        for row in rows:
            key = row["key"]
            duplicate = key in seen
            seen.add(key)
            direct = [] if duplicate else sorted(points_by_chunk[key])
            assessed.append({**row, "directPointIds": direct,
                             "contributionPointIds": sorted(helpers_by_chunk[key]), "duplicate": duplicate})
        judged[stage] = assessed
    stage_points = {stage: {p for row in rows for p in row["directPointIds"]}
                    for stage, rows in judged.items()}
    errors = {}
    for point in points:
        point_id = point["pointId"]
        if point_id not in stage_points["prompt"]:
            if record is None:
                reason = "RUN_MISSING"
            elif point_id not in stage_points["raw"]:
                reason = "RAW_MISS"
            elif point_id not in stage_points["hydrated"]:
                reason = "HYDRATION_LOSS"
            else:
                reason = "PROMPT_LOSS"
            errors[point_id] = reason
    return {"id": query["id"], "type": query["type"], "difficulty": query["difficulty"],
            "category": query["category"], "run": "VALID" if record is not None else "MISSING",
            "elapsedMs": record["elapsedMs"] if record is not None else None,
            "points": {p["pointId"]: p["type"] for p in points},
            "stages": judged, "promptPointErrors": errors}


def ratio(numerator, denominator):
    return numerator / denominator if denominator else None


def summary(rows):
    size = len(rows)
    result = {"expected": size, "valid": sum(row["run"] == "VALID" for row in rows)}
    if not size:
        return result
    duration = sorted(row["elapsedMs"] for row in rows if row["elapsedMs"] is not None)
    result["latencyMs"] = {f"p{p}": duration[math.ceil(p / 100 * len(duration)) - 1]
                           for p in (50, 95, 99)} if duration else None
    result["stages"] = {}
    for stage in STAGES:
        first = Counter()
        total_direct = total_evidence = 0
        point_total = Counter()
        point_covered = Counter()
        for row in rows:
            evidence = row["stages"][stage]
            hits = [item for item in evidence if item["directPointIds"]]
            first[hits[0]["rank"] if hits else "miss"] += 1
            total_direct += len(hits)
            total_evidence += len(evidence)
            covered = {p for item in hits for p in item["directPointIds"]}
            point_total.update(row["points"].values())
            point_covered.update(row["points"][p] for p in covered)
        counts = {f"hitAt{k}": sum(first[r] for r in range(1, k + 1)) for k in (1, 3, 5)}
        result["stages"][stage] = {"totalDirectChunks": total_direct, "totalEvidenceChunks": total_evidence,
                                   "hitCounts": counts, **{k: n / size for k, n in counts.items()},
                                   "mrrAt5": sum(first[r] / r for r in range(1, 6)) / size,
                                   "firstDirectRank": {str(r): first[r] for r in (1, 2, 3, 4, 5, "miss")},
                                   "precisionAt5": total_direct / (TOP_K * size),
                                   "precisionPerReturned": ratio(total_direct, total_evidence),
                                   "emptyQueries": sum(not row["stages"][stage] for row in rows),
                                   "pointCoverage": {kind: {"direct": point_covered[kind], "total": total,
                                                            "ratio": ratio(point_covered[kind], total)}
                                                     for kind, total in sorted(point_total.items())}}
    result["loss"] = {}
    for before, after in zip(STAGES, STAGES[1:]):
        def count(field):
            return result["stages"][before][field] - result["stages"][after][field]

        result["loss"][f"{before}To{after.title()}"] = {
            "allChunks": count("totalEvidenceChunks"),
            "directChunks": count("totalDirectChunks"),
            "hitQueries": (result["stages"][before]["hitCounts"]["hitAt5"]
                           - result["stages"][after]["hitCounts"]["hitAt5"]),
        }
    result["promptPointErrors"] = dict(Counter(error for row in rows for error in row["promptPointErrors"].values()))
    return result


def assess(queries, assignments, hashes, predictions):
    expected = {q["id"]: q for q in queries}
    files = {path.stem: path for path in predictions.glob("*.json")}
    require(set(files) <= set(expected), "Unknown prediction files")
    rows = []
    for query_id, query in expected.items():
        record = read_json(files[query_id]) if query_id in files else None
        if record is not None:
            check_prediction(record, query, hashes)
        rows.append(score_row(record, query, assignments[query_id]))
    groups = {}
    for kind in ("MAIN", "FOLLOW_UP"):
        selected = [row for row in rows if row["type"] == kind]
        groups[kind] = {"overall": summary(selected)}
        for attr in ("category", "difficulty"):
            by_attr = defaultdict(list)
            for row in selected:
                by_attr[row[attr]].append(row)
            groups[kind][f"by{attr.title()}"] = {k: summary(v) for k, v in sorted(by_attr.items())}
    main, followup = (groups[k]["overall"] for k in ("MAIN", "FOLLOW_UP"))
    m_hydrated, m_prompt = (main["stages"][k] for k in ("hydrated", "prompt"))
    f_hydrated = followup["stages"]["hydrated"]
    gate = {"mainValid63": main["valid"] == 63,
            "mainBHitAt5AtLeast57": m_hydrated["hitCounts"]["hitAt5"] >= 57,
            "mainBHitAt1AtLeast45": m_hydrated["hitCounts"]["hitAt1"] >= 45,
            "mainBMRRAtLeast075": m_hydrated["mrrAt5"] >= 0.75,
            "mainCPromptHitAtLeast57": m_prompt["hitCounts"]["hitAt5"] >= 57,
            "mainCPromptPrecisionAtLeast065": (m_prompt["precisionPerReturned"] or 0) >= 0.65,
            "mainCCoreCoverageAtLeast075": m_prompt["pointCoverage"]["CORE"]["ratio"] >= 0.75,
            "followUpValid9": followup["valid"] == 9,
            "followUpBHitAt5AtLeast8": f_hydrated["hitCounts"]["hitAt5"] >= 8}
    return {"scope": "生产集 AI 先验逐点证据映射。未列的点×切片组合未确认直接支持。"
                     "未运行按 miss 保留在分母。完整召回与有害性需独立标签，本工具不宣布验收通过。",
            "countsAndCoverage": groups, "provisionalThresholds": gate, "queries": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--chunk-text-export", type=Path, help="Optional production chunk-key to text JSON for validating exact excerpts")
    parser.add_argument("--predictions", type=Path, help="D3Live production-predictions directory")
    parser.add_argument("--output", type=Path, help="Write metrics JSON into a new file")
    args = parser.parse_args()
    if bool(args.predictions) != bool(args.output):
        parser.error("--predictions and --output must be supplied together")
    queries, points, hashes = frozen_inputs(args.chunk_text_export)
    assignments = assigned_points(queries, points)
    if not args.predictions:
        print(f"questions={len(points)} points={sum(map(len, points.values()))} CORE=77 "
              f"queries={len(queries)} chunks={len(hashes)} exactSpansChecked={bool(args.chunk_text_export)}")
        return
    require(args.predictions.is_dir(), "Prediction directory does not exist")
    result = assess(queries, assignments, hashes, args.predictions)
    with args.output.open("x", encoding="utf-8") as out:
        json.dump(result, out, ensure_ascii=False, indent=2)
        out.write("\n")


if __name__ == "__main__":
    main()
