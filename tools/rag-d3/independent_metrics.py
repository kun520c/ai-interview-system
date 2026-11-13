"""Score the isolated D3 holdout against a rank-free AI judgment file."""

import argparse
from collections import Counter
from hashlib import sha256
import json
import math
import os
from pathlib import Path
from statistics import median


STAGES = {"A": "raw", "B": "hydrated", "C": "prompt"}
LABELS = {"DIRECT", "RELATED_ONLY", "IRRELEVANT", "UNCERTAIN"}
HOLDOUT_SHA = "9f637abce7442fe663c6c0702de907d17dba16ed6e874708ff481c8fbd134632"


def read(path):
    return json.loads(path.read_bytes())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_inputs(run, manifest):
    query_file = run / "independent-queries.json"
    label_file = run / "independent-ai-labels.json"
    query_hash = sha256(query_file.read_bytes()).hexdigest()
    label_file_hash = sha256(label_file.read_bytes()).hexdigest()
    assessor = read(label_file)
    chunks = {
        f"{doc['id']}/{chunk['index']}": chunk["sha256"]
        for doc in read(manifest)["documents"]
        for chunk in doc["chunks"]
    }
    require(query_hash == HOLDOUT_SHA == assessor["querySha256"], "Holdout hash mismatch")
    require(sha256(manifest.read_bytes()).hexdigest() == assessor["manifestSha256"], "Corpus hash mismatch")
    sources = assessor["sourceHashes"]
    require(set(sources) == {"independent-assessor-a.json", "independent-assessor-b.json"}, "Assessor catalog mismatch")
    for name, digest in sources.items():
        require(sha256((run / name).read_bytes()).hexdigest() == digest, "Assessor seal mismatch")
    require(chunks == assessor["chunks"] and assessor["defaultLabel"] == "IRRELEVANT", "Chunk catalog mismatch")
    queries = {row["id"]: row for row in read(query_file)["queries"]}
    judgments = {row["queryId"]: row for row in assessor["queries"]}
    require(len(queries) == 24 and judgments.keys() == queries.keys(), "Query catalog mismatch")
    require(
        Counter(q["expectedEvidence"] for q in queries.values()) == {"DIRECT_EXPECTED": 18, "NO_DIRECT_EXPECTED": 6},
        "Unexpected expected-evidence counts",
    )
    for query_id, judgment in judgments.items():
        require(judgment["expectedEvidence"] == queries[query_id]["expectedEvidence"], f"Expectation mismatch {query_id}")
        require(judgment["labels"].keys() <= chunks.keys(), f"Judgment has an unknown chunk {query_id}")
        for label in judgment["labels"].values():
            require(label["label"] in LABELS, f"Unknown label {query_id}")
            if label["label"] == "DIRECT":
                require(judgment["expectedEvidence"] == "DIRECT_EXPECTED", f"Negative expectation conflict {query_id}")
                require(bool(label["supportedIntentIds"]) and set(label["supportedIntentIds"]) <= judgment["intentIds"].keys(),
                        f"Invalid evidence intent mapping {query_id}")
    return queries, judgments, chunks, query_hash, label_file_hash


def collect_stages(prediction, judgment, chunks):
    stages = {}
    prior = {}
    for name, field in STAGES.items():
        items = prediction[field]
        ranks = [item["rank"] for item in items]
        require(ranks == sorted(set(ranks)) and all(isinstance(rank, int) and 1 <= rank <= 5 for rank in ranks),
                f"Invalid {name} ranks for {prediction['id']}")
        if name == "A":
            require(ranks == list(range(1, len(items) + 1)), f"Noncontiguous A ranks for {prediction['id']}")
        seen = set()
        ranked = []
        for item in items:
            key, rank = item["key"], item["rank"]
            require(key in chunks, f"Unknown chunk {key} for {prediction['id']}")
            if name == "A":
                require(math.isfinite(item["score"]), f"Nonfinite score for {prediction['id']}")
            else:
                require(prior.get(rank) == key and item["sha256"] == chunks[key],
                        f"Changed stage identity for {prediction['id']}")
            label = judgment["labels"].get(key, {"label": "IRRELEVANT"})
            duplicate = key in seen
            ranked.append(dict(rank=rank, key=key, label=label["label"], duplicate=duplicate,
                               harmful=bool(label.get("harmful", False)),
                               intents=label.get("supportedIntentIds", [])))
            seen.add(key)
        prior = {row["rank"]: row["key"] for row in ranked}
        stages[name] = ranked
    return stages


def aggregate(rows):
    positive = [row for row in rows if row["expectedEvidence"] == "DIRECT_EXPECTED"]
    negative = [row for row in rows if row["expectedEvidence"] == "NO_DIRECT_EXPECTED"]
    result = {}
    for name in STAGES:
        direct = [[item for item in row["stages"][name] if item["label"] == "DIRECT" and not item["duplicate"]]
                  for row in positive]
        ranks = [hits[0]["rank"] if hits else None for hits in direct]
        relevant = sum(len(hits) for hits in direct)
        returned = sum(len(row["stages"][name]) for row in positive)
        cross_topic = [(row, hits) for row, hits in zip(positive, direct) if row["type"] == "CROSS_TOPIC"]
        covered = [len({intent for item in hits for intent in item["intents"]}) for _, hits in cross_topic]
        expected = sum(row["intentCount"] for row, _ in cross_topic)
        negative_labels = Counter(item["label"] for row in negative for item in row["stages"][name])
        result[name] = {
            "positiveCount": len(positive),
            "hitNumerators": {str(k): sum(rank is not None and rank <= k for rank in ranks) for k in (1, 3, 5)},
            "firstDirectRankCounts": dict(Counter(str(rank) if rank is not None else "miss" for rank in ranks)),
            "mrrAt5": sum(1 / rank for rank in ranks if rank is not None) / len(positive) if positive else None,
            "precisionAt5": relevant / (5 * len(positive)) if positive else None,
            "direct": relevant, "positiveReturned": returned,
            "positiveActualPrecision": relevant / returned if returned else None,
            "crossTopicIntentCoverage": {"supported": sum(covered), "expected": expected,
                                         "complete": sum(count == row["intentCount"]
                                                         for count, (row, _) in zip(covered, cross_topic)),
                                         "queries": len(cross_topic)},
            "negativeCount": len(negative),
            "negativeNonEmpty": sum(bool(row["stages"][name]) for row in negative),
            "negativeReturnedLabels": dict(negative_labels),
            "harmfulEvidenceQueries": sum(any(item["harmful"] for item in row["stages"][name]) for row in rows),
        }
    return result


def evaluate(queries, judgments, chunks, predictions):
    require(predictions.is_dir(), "Missing live holdout predictions; no metrics calculated")
    require({p.stem for p in predictions.glob("*.json")} <= queries.keys(), "Unexpected holdout predictions")
    rows = []
    for query_id, query in queries.items():
        path = predictions / f"{query_id}.json"
        row = dict(id=query_id, expectedEvidence=query["expectedEvidence"], domains=query["domains"],
                   type=query["type"], intentCount=len(judgments[query_id]["intentIds"]))
        if path.exists():
            prediction = read(path)
            require(prediction["id"] == query_id, f"Prediction ID mismatch {query_id}")
            row["stages"] = collect_stages(prediction, judgments[query_id], chunks)
            row["elapsedMs"] = prediction["elapsedMs"]
            row["embeddingUsage"] = prediction["embeddingUsage"]
        else:
            row["stages"] = {name: [] for name in STAGES}
            row["error"] = "NO_LIVE_RESULT"
        rows.append(row)
    valid = [row for row in rows if "error" not in row]
    require(valid, "No live holdout results; metrics unavailable")
    counts = aggregate(rows)
    c = counts["C"]
    prompt_count = c["positiveReturned"] + sum(c["negativeReturnedLabels"].values())
    groups = {
        "domain": {domain: aggregate([row for row in rows if domain in row["domains"]])
                   for domain in sorted({domain for row in rows for domain in row["domains"]})},
        "type": {kind: aggregate([row for row in rows if kind == row["type"]])
                 for kind in sorted({row["type"] for row in rows})},
    }
    return {
        "labelConfidence": "Two rank-blinded AI judgments, reconciled by one assessor; provisional AI labels",
        "valid": len(valid), "planned": len(rows), "missing": [row["id"] for row in rows if "error" in row],
        "stages": counts,
        "promptDirectRate": {"direct": c["direct"], "returned": prompt_count,
                             "fraction": c["direct"] / prompt_count if prompt_count else None},
        "positiveCorpusGaps": [row["id"] for row in rows if row["expectedEvidence"] == "DIRECT_EXPECTED"
                               and all(value["label"] != "DIRECT" for value in judgments[row["id"]]["labels"].values())],
        "candidateLoss": {
            f"{before}->{after}": {
                "returned": sum(len(row["stages"][before]) - len(row["stages"][after]) for row in valid),
                "direct": counts[before]["direct"] - counts[after]["direct"],
            }
            for before, after in (("A", "B"), ("B", "C"))
        },
        "medianElapsedMs": median(row["elapsedMs"] for row in valid),
        "maxElapsedMs": max(row["elapsedMs"] for row in valid),
        "embeddingUsageTokens": sum(row["embeddingUsage"] for row in valid),
        "groups": groups, "queryRows": rows,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, default=Path("backups/D3_BCD_20261114"))
    parser.add_argument("--manifest", type=Path, default=Path("docs/rag/d3-b/CORPUS_MANIFEST_20261114.json"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    queries, judgments, chunks, query_hash, label_hash = load_inputs(args.run, args.manifest)
    result = evaluate(queries, judgments, chunks, args.run / "independent-predictions")
    result.update(querySha256=query_hash, judgmentSha256=label_hash)
    os.umask(0o077)
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")


if __name__ == "__main__":
    main()
