"""Offline-only P1 input verification and Float32 threshold sweep. No service clients."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import struct


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def f32(value):
    return struct.unpack(">f", struct.pack(">f", float(value)))[0]


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def content_rule_candidates(text):
    """Review flags only; never used by production retrieval or to change human labels."""
    if not text.strip():
        return ["EMPTY_TEXT"]
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    flags = []
    fences = [re.match(r"^(?:```|~~~)", line) for line in lines]
    if sum(bool(fence) for fence in fences) % 2:
        flags.append("UNBALANCED_FENCE_REVIEW_WITH_NEIGHBOR")
    reference = re.compile(r"^(?:\[[^\]]+\]:\s*https?://|[-*]\s*\[[^\]]+\]\s|https?://)")
    references = [bool(reference.match(line)) for line in lines]
    other = [line for line, is_ref in zip(lines, references) if not is_ref]
    # A leading source-list continuation and a project metadata note need review too.
    metadata = re.compile(r"^(?:#{1,6}\s.*[Rr]eferences|Official references|项目类比：)")
    substantive = [line for line in other if not metadata.match(line)]
    source_label = re.compile(r"^(?:MySQL\s+\d|Spring\s+\d|Java SE\s+\d|RFC\s*\d)")
    if any(references) and (not substantive or
                           (len(substantive) == 1 and substantive[0] == lines[0]
                            and source_label.match(substantive[0])
                            and not re.search(r"[。！？]", substantive[0])
                            and lines[1].startswith("- ["))):
        flags.append("REFERENCE_ONLY_OR_METADATA_REVIEW")
    return flags


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--labels", type=Path, required=True)
    parser.add_argument("--ai-review", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    root = args.root.resolve()
    epoch = "LOCAL_20261011T103308698Z"
    raw_path = root / "target/local-rag-live-runs" / epoch / "local-rag-result.json"
    bank_path = root / "docs/evaluation/judge/PHASE_5A_JUDGE_BENCHMARK.json"
    raw, labels, review, bank = map(read, [raw_path, args.labels, args.ai_review, bank_path])
    assert raw["status"] == "LOCAL_READONLY_TOPK5_COMPLETE"
    assert raw["metric"] == "COSINE" and raw["topK"] == 5
    assert raw["epoch"] == labels["epoch"] == epoch
    assert review["sourceSha256"] == sha(args.labels)
    topics = {t["bankId"]: t for t in bank["topics"]}
    cases = {c["caseId"]: c for c in bank["cases"]}
    queries = {q["queryId"]: q for q in raw["queries"]}
    candidates = {(c["queryId"], c["rawRank"]): c for c in raw["candidates"]}
    fixture_queries = []
    for q in labels["queries"]:
        assert q["queryHash"] == queries[q["queryId"]]["queryHash"]
        assert hashlib.sha256(q["embeddedQuery"].encode()).hexdigest() == q["queryHash"]
        topic, case = topics[q["bankId"]], cases[q["embeddingEventCaseId"]]
        assert topic["questionContent"] == q["originalQuestion"]
        assert len(q["hits"]) == 5
        hits = []
        for h, vector in zip(q["hits"], queries[q["queryId"]]["rawHits"], strict=True):
            c = candidates[q["queryId"], h["rank"]]
            assert h["rank"] == vector["rawRank"] == c["rawRank"]
            assert h["vectorId"] == vector["vectorId"] == c["vectorId"]
            assert h["stableEvidenceId"] == c["stableEvidenceId"]
            assert h["scoreFloat32BitsHex"] == vector["scoreFloat32BitsHex"] == c["scoreFloat32BitsHex"]
            assert struct.pack(">f", float(h["cosineScore"])).hex() == h["scoreFloat32BitsHex"]
            assert h["content"] == c["excerpt"]
            assert hashlib.sha256(h["content"].encode()).hexdigest() == c["contentSha256"]
            hits.append(dict(h, chunkId=c["chunkId"], documentVersion=c["documentVersion"],
                             embeddingVersion=vector["embeddingVersion"]))
        fixture_queries.append(dict(q, hits=hits, referenceAnswer=topic["referenceAnswerSnapshot"],
                                    scoringPoints=topic["scoringPoints"],
                                    followUpTargetPointIds=case["followUpTargetPointIds"]))
    assert len(fixture_queries) == 8 and len(candidates) == 40
    for p in review["proposals"]:
        h = next(h for q in labels["queries"] if q["queryId"] == p["queryId"]
                 for h in q["hits"] if h["rank"] == p["rank"])
        assert h["vectorId"] == p["vectorId"] and h["humanLabel"] == p["currentHumanLabel"]
    sources = {str(p): sha(p) for p in [raw_path, args.labels, args.ai_review, bank_path]}
    fixture = dict(format="rag-p1-offline-replay-v1", epoch=epoch, metric="COSINE", topK=5,
                   scorePrecision="FLOAT32", embeddingModel="qwen3.7-text-embedding",
                   sourceSha256=sources, aiReview=review, queries=fixture_queries)
    write(root / "src/test/resources/rag-p1/local-replay.json", fixture)
    sweep = []
    for threshold in [0.60, 0.70, 0.75, 0.80]:
        rows, kept_labels, removed_labels = [], Counter(), Counter()
        for q in fixture_queries:
            kept = [h for h in q["hits"] if f32(h["cosineScore"]) >= f32(threshold)]
            removed = [h for h in q["hits"] if h not in kept]
            kept_labels.update(h["humanLabel"] for h in kept)
            removed_labels.update(h["humanLabel"] for h in removed)
            rows.append(dict(queryId=q["queryId"], count=len(kept),
                             retainedRanks=[h["rank"] for h in kept],
                             retainedVectorIds=[h["vectorId"] for h in kept],
                             filteredVectorIds=[h["vectorId"] for h in removed]))
        sweep.append(dict(threshold=threshold, retained=sum(kept_labels.values()),
                          retainedHumanLabels=dict(kept_labels), removedHumanLabels=dict(removed_labels),
                          zeroResultQueries=sum(r["count"] == 0 for r in rows), queries=rows))
    write(root / "target/rag-p1-20261012/offline-sweep.json",
          dict(sourceSha256=sources, goldChanged=False, aiSuggestionsApplied=False, sweep=sweep))
    issues = []
    for q in fixture_queries:
        for h in q["hits"]:
            flags = content_rule_candidates(h["content"])
            if flags:
                issues.append(dict(queryId=q["queryId"], rank=h["rank"], chunkId=h["chunkId"],
                                   documentId=h["documentId"], chunkIndex=h["chunkIndex"],
                                   vectorId=h["vectorId"], humanLabel=h["humanLabel"],
                                   retainedAt070=f32(h["cosineScore"]) >= f32(0.70), flags=flags))
    write(root / "target/rag-p1-20261012/content-rule-candidates.json",
          dict(productionFilteringEnabled=False, humanLabelsChanged=False, issues=issues))
    print(json.dumps([dict(threshold=s["threshold"], retained=s["retained"],
                           labels=s["retainedHumanLabels"], counts=[q["count"] for q in s["queries"]])
                      for s in sweep], ensure_ascii=False))


if __name__ == "__main__":
    main()
