#!/usr/bin/env python3
"""Replay four D3-E selections over the original D3 search and Prompt records, offline."""

import argparse
from collections import Counter
from hashlib import sha256
import json
import math
from pathlib import Path
import re
from statistics import mean, median
from struct import pack, unpack

import independent_metrics as independent
import production_metrics as production
import public_labels as public


ROOT = public.ROOT
MANIFEST = public.MANIFEST
D3B = MANIFEST.parent
D3D = public.LABEL_DIR
TOP_K = production.TOP_K
OPTIONS = ("A", "B", "C", "D")
# Compare the same Float32 values as RetrievalProperties and KnowledgeRetrievalService.
def float32(value):
    return unpack("!f", pack("!f", value))[0]


CUTOFF = float32(0.70)
CHUNK = re.compile(
    r"^- rank=(\d+), similarity=([-\d.]+)\n  title=([^\n]*)\n  category=([^\n]*)\n"
    r"  source=[^\n]*\n  content=(.*?)(?=\n\n- rank=|\n\n\Z)\n\n",
    re.MULTILINE | re.DOTALL,
)
EMPTY_EVIDENCE = "无可用 RAG Evidence。\n"
READ = public.read
REQUIRE = public.require
SHA = public.digest


def divide(numerator, denominator):
    return numerator / denominator if denominator else None


def indexes():
    docs = READ(MANIFEST)["documents"]
    reconciliation = READ(D3B / "INGESTION_RECONCILIATION_20261114.json")
    ingested = {doc["key"]: doc for doc in reconciliation["documents"]}
    REQUIRE(len(docs) == len(ingested) == 51, "Document count")
    catalog = {}
    for doc in docs:
        key = doc["id"]
        source = (ROOT / doc["file"]).read_bytes()
        REQUIRE(sha256(source).hexdigest() == doc["fileSha256"], f"Source bytes changed: {key}")
        normalized = source.decode("utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
        REQUIRE(sha256(normalized.encode()).hexdigest() == doc["normalizedSha256"], key)
        saved = ingested[key]
        REQUIRE(saved["sha256"] == doc["normalizedSha256"] and saved["version"] == doc["documentVersion"], key)
        REQUIRE(len(saved["chunks"]) == len(doc["chunks"]), key)
        for chunk, row in zip(doc["chunks"], saved["chunks"]):
            chunk_key = f"{key}/{chunk['index']}"
            REQUIRE((row["key"], row["index"], row["sha256"]) == (chunk_key, chunk["index"], chunk["sha256"]), key)
            REQUIRE(chunk_key not in catalog, f"Duplicate chunk {chunk_key}")
            catalog[chunk_key] = {**row, **chunk, "title": doc["title"], "category": doc["category"],
                                  "normalized": normalized}
    REQUIRE(len(catalog) == 52, "Chunk count")
    for identity in ("chunkId", "vectorId"):
        REQUIRE(len({row[identity] for row in catalog.values()}) == 52, f"Duplicate {identity}")
    return docs, catalog, reconciliation


def check_prediction(record, query_id, catalog, texts):
    REQUIRE(record["id"] == query_id, f"Prediction path disagrees with {query_id}")
    raw, hydrated, selected = (record[stage] for stage in production.STAGES)
    by_rank = {item["rank"]: item for item in raw}
    for item in raw:
        REQUIRE(item["vectorId"] == catalog[item["key"]]["vectorId"], f"Unknown vector for {query_id}")
        REQUIRE(float32(item["score"]) == item["score"], f"Original score is not Float32 for {query_id}")
    for item in hydrated:
        REQUIRE(item["chunkId"] == catalog[item["key"]]["chunkId"], f"Unknown chunk ID for {query_id}")

    body = record["promptEvidence"]
    matches = list(CHUNK.finditer(body))
    reconstructed = "".join(match.group() for match in matches) if selected else EMPTY_EVIDENCE
    REQUIRE(body == reconstructed and len(matches) == len(selected), f"Prompt entry mismatch for {query_id}")
    measures = {}
    for match, item in zip(matches, selected):
        rank, score, title, category, content = match.groups()
        chunk = catalog[item["key"]]
        REQUIRE(int(rank) == item["rank"] and abs(float(score) - by_rank[int(rank)]["score"]) <= 0.000000500001,
                f"Prompt rank or score mismatch for {query_id}")
        REQUIRE((title, category) == (chunk["title"], chunk["category"]), f"Prompt metadata mismatch for {query_id}")
        REQUIRE(sha256(content.encode()).hexdigest() == chunk["sha256"] == item["sha256"],
                f"Prompt content digest mismatch for {query_id}")
        length = public.utf16_length(content)
        REQUIRE(length == chunk["utf16Length"] and len(content.encode()) == chunk["utf8Bytes"]
                and content in chunk["normalized"], f"Prompt content differs from frozen source for {query_id}")
        texts[item["key"]] = content
        measures[int(rank)] = (length, public.utf16_length(match.group()))
    return measures


def load_predictions(run, query_sets, catalog):
    records, texts = {}, {}
    hashes = {key: row["sha256"] for key, row in catalog.items()}
    for name, queries in query_sets.items():
        paths = {path.stem: path for path in (run / f"{name}-predictions").glob("*.json")}
        REQUIRE(paths.keys() == queries.keys(), f"Missing or extra original predictions: {name}")
        for query_id, query in queries.items():
            record = READ(paths[query_id])
            production.check_prediction(record, query if name == "production" else record, hashes)
            if name != "production":
                REQUIRE((record["type"], record["category"], record["difficulty"]) ==
                        (name.upper(), query["domains"][0], "N/A"), f"Prediction metadata differs for {query_id}")
            measures = check_prediction(record, query_id, catalog, texts)
            records[query_id] = (record, measures, SHA(paths[query_id]))
    REQUIRE(texts.keys() == catalog.keys(), "Original Prompts do not cover all frozen chunks")
    return records, texts


def pick(judged, measures):
    raw, hydrated, baseline = (judged[stage] for stage in production.STAGES)
    raw_scores = {item["rank"]: item["score"] for item in raw}
    selections = {"raw": raw, "hydrated": hydrated, "A": baseline, "B": hydrated[:1], "C": hydrated[:2],
                  "D": [item for item in hydrated if raw_scores[item["rank"]] >= CUTOFF]}
    # The original builder emits each hydrated item in full. Only that audited run can be replayed this way.
    REQUIRE([row["rank"] for row in hydrated] == list(measures), "Hydrated/Prompt mismatch: stop before replay")
    return selections


def stage(items, required, measures=None):
    counts = Counter(row["label"] for row in items)
    seen, direct = set(), []
    for row in items:
        key = row["key"]
        if row["label"] == "DIRECT" and key not in seen:
            direct.append(row)
        seen.add(key)
    uncredited = counts["DIRECT"] - len(direct)
    counts["DIRECT"] = len(direct)
    result = {"count": len(items), "labels": dict(sorted(counts.items())),
              "duplicateChunkCount": len(items) - len(seen),
              "uncreditedDuplicateDirectCount": uncredited,
              "firstDirectRawRank": direct[0]["rank"] if direct else None,
              "covered": sorted({item for row in direct for item in row["supported"]} & required),
              "containsHarmfulLabel": any(row.get("harmful", False) for row in items)}
    if measures is not None:
        lengths = [measures[row["rank"]] for row in items]
        result["chunkUtf16"] = sum(item[0] for item in lengths)
        result["sectionUtf16"] = sum(item[1] for item in lengths) if lengths else public.utf16_length(EMPTY_EVIDENCE)
    return result


def labels_for(record, chunk_labels):
    stages = {}
    for key in production.STAGES:
        items = []
        for row in record[key]:
            chunk = row["key"]
            label = chunk_labels.get(chunk, {"label": "IRRELEVANT"})
            items.append({**row, "label": label["label"], "supported": label.get("supportedIntentIds", []),
                          "harmful": bool(label.get("harmful"))})
        stages[key] = items
    return stages


def row_data(query, label_map, mechanism_ids, core_ids, record, measures, prediction_hash):
    judged = labels_for(record, label_map)
    selections = pick(judged, measures)
    results = {}
    for name, items in selections.items():
        result = stage(items, mechanism_ids, measures if name in OPTIONS else None)
        if name in OPTIONS[1:]:
            lost = set(results["A"]["covered"]) - set(result["covered"])
            result["lostMechanismsVsA"] = sorted(lost)
            result["lostCoreVsA"] = sorted(lost & core_ids)
        if name in OPTIONS[1:]:
            result["retainedOriginalRanks"] = [item["rank"] for item in items]
        results[name] = result

    expected = query.get("expectedEvidence", "DIRECT_EXPECTED") == "DIRECT_EXPECTED"
    direct_in_corpus = sum(row["label"] == "DIRECT" for row in label_map.values())
    losses = {}
    for before, after in (("raw", "hydrated"), ("hydrated", "A")):
        retained = {item["rank"] for item in selections[after]}
        losses[f"{before}->{after}"] = [item["rank"] for item in selections[before] if item["rank"] not in retained]
    return {"id": query["id"], "type": query["type"], "expectedDirect": expected,
            "corpusDirectCountUnderLabels": direct_in_corpus, "requiredMechanisms": sorted(mechanism_ids),
            "coreMechanisms": sorted(core_ids), "transitionLostRanks": losses,
            "originalElapsedMs": record["elapsedMs"], "originalEmbeddingInputTokens": record["embeddingUsage"],
            "originalPredictionSha256": prediction_hash, "stages": results}


def aggregate(rows, stage_key):
    stages = [row["stages"][stage_key] for row in rows]
    counts = Counter()
    for state in stages:
        counts.update(state["labels"])
    returned = sum(state["count"] for state in stages)
    n_direct = counts["DIRECT"]
    first = [state["firstDirectRawRank"] for state in stages]
    nonempty = [state for state in stages if state["count"]]
    empty = len(stages) - len(nonempty)
    gate_stats = {"queries": len(rows), "returned": returned, "labels": counts, "emptyQueries": empty,
                  "emptyRate": divide(empty, len(rows)), "averageEntries": divide(returned, len(rows)),
                  "duplicateChunkCount": sum(state["duplicateChunkCount"] for state in stages),
                  "uncreditedDuplicateDirectCount": sum(state["uncreditedDuplicateDirectCount"] for state in stages),
                  "precisionMicro": divide(n_direct, returned),
                  "precisionMacroNonEmpty": mean(state["labels"].get("DIRECT", 0) / state["count"]
                                                 for state in nonempty) if nonempty else None,
                  "harmfulLabeledQueries": sum(state["containsHarmfulLabel"] for state in stages),
                  "hitCountsByOriginalRank": {str(k): sum(rank is not None and rank <= k for rank in first)
                                              for k in (1, 3, 5)},
                  "mrrAt5": divide(sum(1 / rank for rank in first if rank is not None), len(rows)),
                  "pAt5FixedDenominator": divide(n_direct, len(rows) * TOP_K)}
    if stage_key in OPTIONS:
        gate_stats.update(averageChunkUtf16=divide(sum(state["chunkUtf16"] for state in stages), returned),
                          averageSectionUtf16=divide(sum(state["sectionUtf16"] for state in stages), len(rows)),
                          evidenceTokenCount=None)
    return gate_stats


def distribution(values):
    return {"count": len(values), "min": min(values), "median": median(values), "max": max(values)}


def score_distributions(rows, scores):
    by_label = {}
    top1 = {}
    for row in rows:
        items = scores[row["id"]]
        for label, score in items:
            by_label.setdefault(label, []).append(score)
        if items:
            top1.setdefault("positive" if row["expectedDirect"] else "negative", []).append(items[0][1])
    return {"byRawCandidateLabel": {k: distribution(v) for k, v in sorted(by_label.items())},
            "top1ByExpectation": {k: distribution(v) for k, v in sorted(top1.items())}}


def summarize(rows, scores):
    positive = [row for row in rows if row["expectedDirect"]]
    negative = [row for row in rows if not row["expectedDirect"]]
    has_corpus_direct = [row for row in positive if row["corpusDirectCountUnderLabels"]]
    times = sorted(row["originalElapsedMs"] for row in rows)
    result = {"queries": len(rows), "positiveQueries": len(positive), "negativeQueries": len(negative),
              "corpusPositiveWithKnownDirect": len(has_corpus_direct),
              "corpusDirectCountHistogramForPositive": dict(sorted(Counter(
                  row["corpusDirectCountUnderLabels"] for row in positive).items())),
              "historicalElapsedMs": {"median": median(times), "p95NearestRank": times[math.ceil(.95 * len(times)) - 1]},
              "historicalEmbeddingInputs": sum(row["originalEmbeddingInputTokens"] for row in rows),
              "rawScoreDistribution": score_distributions(rows, scores),
              "stages": {}}
    for name in rows[0]["stages"]:
        s = result["stages"][name] = {"all": aggregate(rows, name), "positive": aggregate(positive, name),
                                      "negative": aggregate(negative, name)}
        ps = [row["stages"][name] for row in positive]
        s["negativeFalseProvision"] = divide(len(negative) - s["negative"]["emptyQueries"], len(negative))
        s["positiveEmptyRate"] = s["positive"]["emptyRate"]
        s["positiveEmptyWithKnownCorpusDirect"] = sum(not row["stages"][name]["count"] for row in has_corpus_direct)
        s["positiveMisses"] = sum(state["firstDirectRawRank"] is None for state in ps)
        s["corpusAnswerableDiagnosticHit"] = {
            "hit": sum(row["stages"][name]["firstDirectRawRank"] is not None for row in has_corpus_direct),
            "queries": len(has_corpus_direct),
        }
        if name in OPTIONS:
            s["coveredMechanisms"] = {"direct": sum(len(state["covered"]) for state in ps),
                                       "required": sum(len(row["requiredMechanisms"]) for row in positive)}
            s["coveredCore"] = {"direct": sum(len(set(state["covered"]) & set(row["coreMechanisms"]))
                                              for row, state in zip(positive, ps)),
                                "required": sum(len(row["coreMechanisms"]) for row in positive)}
            cross = [(row, state) for row, state in zip(positive, ps) if row["type"] in {"跨主题", "CROSS_TOPIC"}]
            s["crossTopicCoverage"] = {"covered": sum(len(state["covered"]) for _, state in cross),
                                       "required": sum(len(row["requiredMechanisms"]) for row, _ in cross),
                                       "complete": sum(state["covered"] == row["requiredMechanisms"] for row, state in cross),
                                       "queries": len(cross)}
    return result


def verify_legacy(groups, run, prod_queries, assignments, hashes, independent_recomputed, independent_original):
    REQUIRE(independent_recomputed == independent_original, "Independent original result changed on replay")
    public_old = READ(D3D / "PUBLIC_RETRIEVAL_RESULTS_20261114.json")
    prod_old = READ(D3D / "PRODUCTION_RETRIEVAL_METRICS_20261114.json")
    REQUIRE(production.assess(prod_queries, assignments, hashes, run / "production-predictions") == prod_old,
            "Production original result changed on replay")
    REQUIRE(public.score(run / "public-predictions", public.FROZEN_LABELS) == public_old,
            "Public original result changed on replay")
    for name in ("MAIN", "FOLLOW_UP"):
        old = prod_old["countsAndCoverage"][name]["overall"]["stages"]["prompt"]
        actual = groups[name]["stages"]["A"]["all"]
        REQUIRE((actual["labels"]["DIRECT"], actual["returned"]) ==
                (old["totalDirectChunks"], old["totalEvidenceChunks"]), f"Preserve original {name} denominator")
    for name, old in (("public", public_old["allIncludingExpectedNegatives"]["C"]),
                      ("independent", independent_original["promptDirectRate"])):
        actual = groups[name]["stages"]["A"]["all"]
        REQUIRE((actual["labels"]["DIRECT"], actual["returned"]) == (old["direct"], old["returned"]),
                f"Preserve original {name} denominator")


def run_replay(run):
    docs, catalog, reconciliation = indexes()
    prod_queries, prod_points, hashes = production.frozen_inputs(None)
    assignments = production.assigned_points(prod_queries, prod_points)
    pub_queries, _ = public.catalog()
    ind_queries, ind_labels, ind_chunks, query_hash, label_hash = independent.load_inputs(run, MANIFEST)
    sources = {"production": {query["id"]: query for query in prod_queries},
               "public": pub_queries, "independent": ind_queries}
    records, texts = load_predictions(run, sources, catalog)

    production.validate_spans([point for points in prod_points.values() for point in points], hashes, texts)
    pub_labels = READ(public.FROZEN_LABELS)
    REQUIRE(public.freeze(texts) == pub_labels, "Public labels or note disagreement changed on replay")
    public.verify_spans({(key, chunk): label for key, row in pub_labels["queries"].items()
                         for chunk, label in row.items()}, texts, hashes)
    ind_frozen = READ(run / "independent-ai-labels.json")
    public.verify_spans({(key, chunk): label for key, row in ind_labels.items()
                         for chunk, label in row["labels"].items()}, texts, hashes)
    REQUIRE(ind_chunks == hashes, "Independent chunk catalog differs")

    rows_by_group = {name: [] for name in ("MAIN", "FOLLOW_UP", "public", "independent")}
    scores = {}
    for source, queries in sources.items():
        for key, query in queries.items():
            core = set()
            if source == "production":
                group, points = query["type"], assignments[key]
                required = {point["pointId"] for point in points}
                core = {point["pointId"] for point in points if point["type"] == "CORE"}
                labels = {}
                for point in points:
                    for chunk, evidence in point["evidence"].items():
                        label = labels.setdefault(chunk, {"label": "CONTRIBUTION_ONLY", "supportedIntentIds": []})
                        if evidence["role"] == "DIRECT":
                            label["label"] = "DIRECT"
                            label["supportedIntentIds"].append(point["pointId"])
                labels.update({chunk: {"label": "UNASSESSED"} for chunk in hashes.keys() - labels.keys()})
            elif source == "public":
                group, labels = source, pub_labels["queries"][key]
                required = set(query.get("fullAnswerRequires", []))
                if not required and query["expectedEvidence"] == "DIRECT_EXPECTED":
                    required = {f"{key}-I1"}
            else:
                group, label_row = source, ind_labels[key]
                required = set(label_row["intentIds"])
                labels = label_row["labels"]
            scores[key] = [(labels.get(hit["key"], {"label": "IRRELEVANT"})["label"], hit["score"])
                           for hit in records[key][0]["raw"]]
            rows_by_group[group].append(row_data(query, labels, required, core, *records[key]))

    groups = {name: summarize(rows, scores) for name, rows in rows_by_group.items()}
    old_independent = independent.evaluate(ind_queries, ind_labels, ind_chunks, run / "independent-predictions")
    old_independent.update(querySha256=query_hash, judgmentSha256=label_hash)
    verify_legacy(groups, run, prod_queries, assignments, hashes, old_independent, READ(run / "independent-results.json"))
    lengths = [chunk["utf16Length"] for chunk in catalog.values()]
    historical_usage = READ(D3D / "API_USAGE_20261114.json")["actual"]
    return {"version": "D3-E-evidence-replay-v1-20261114",
            "boundary": "Original 2026-11-14 paid retrieval records, replayed offline. A is historical exact serialized Prompt evidence. B/C/D only omit entries. No new model input or score output is measured here.",
            "selection": {"A": "Historical full eligible Prompt evidence, threshold disabled",
                          "B": "First eligible result", "C": "First two eligible results",
                          "D": "Existing inclusive 0.70F cutoff, no additional cap. Declared by the original protocol before result; no calibration or search.",
                          "topK": TOP_K, "thresholdFloat32": CUTOFF, "promptVersion": "evaluation-prompt-v3",
                          "embeddingModel": reconciliation["embeddingModel"], "embeddingProfile": reconciliation["embeddingProfile"],
                          "embeddingDimension": reconciliation["dimension"], "metric": reconciliation["metric"],
                          "index": reconciliation["index"]},
            "metricNotes": {
                "precisionMicro": "First occurrence of each DIRECT chunk per query divided by actual returned entries; later duplicates remain in denominator. Includes negatives for all.",
                "precisionMacroNonEmpty": "Mean of per-query values over non-empty queries only. Empty precision is undefined.",
                "hitCountsByOriginalRank": "Any DIRECT at original rank ≤ k; absence is a miss, irrespective of display position.",
                "pAt5FixedDenominator": "DIRECT count divided by 5 × queries; empty ranks count zero. Positive candidate evaluation uses only frozen positive queries.",
                "positiveEmptyRate": "No selected evidence, whether inherently lacking corpus support or not. Known corpus DIRECT empty count and no-DIRECT misses reported separately.",
                "productionLabelLimit": "Point labels identify DIRECT and CONTRIBUTION_ONLY. Unmapped chunks are UNASSESSED, not reclassified as RELATED_ONLY, IRRELEVANT, or harmless.",
                "coverage": "Production uses assigned score point IDs, follow-up uses only the target CORE. Public regular queries use an implicit intent, cross-topic uses frozen fullAnswerRequires. Independent uses adjudicated intent IDs. Only distinct directly supported IDs count.",
                "length": "Exact UTF-16 from the archived Prompt string, including metadata and empty marker for section length. No provider token count for this section. Alternative lengths are reconstructed by omission.",
                "latency": "Historical embedding, search, hydration and formatting wall time. Replay has no live comparative latency.",
                "recall": "No certified human-labeled complete relevance set. No exact recall claimed.",
                "duplicate": "The frozen data has no repeats. This replay does not simulate introducing a deduplication rule.",
            },
            "sourceSha256": {"manifest": SHA(MANIFEST), "ingestionReconciliation": SHA(D3B / "INGESTION_RECONCILIATION_20261114.json"),
                             "originalPublicQueries": SHA(public.PUBLIC), "publicLabels": SHA(public.FROZEN_LABELS),
                             "productionPoints": SHA(production.LABELS), "productionQueries": SHA(production.INPUTS),
                             "independentQueries": query_hash, "independentLabels": label_hash},
            "corpus": {"documentCount": len(docs), "chunkCount": len(catalog), "chunkCountsPerDocument": dict(sorted(Counter(
                        len(doc["chunks"]) for doc in docs).items())), "minChunkUtf16": min(lengths),
                       "medianChunkUtf16": median(lengths), "maxChunkUtf16": max(lengths)},
            "validatedOriginals": {"predictions": len(records), "allFrozenChunksObserved": len(texts),
                                   "rawEntries": sum(len(record["raw"]) for record, _, _ in records.values()),
                                   "hydratedEntries": sum(len(record["hydrated"]) for record, _, _ in records.values()),
                                   "serializedEntries": sum(len(record["prompt"]) for record, _, _ in records.values()),
                                   "queriesWithFewerThan5Raw": sum(len(record["raw"]) < TOP_K for record, _, _ in records.values()),
                                   "queriesWithFewerThan5PromptEntries": sum(len(record["prompt"]) < TOP_K
                                                                               for record, _, _ in records.values()),
                                   "repeatedPromptChunkKeys": sum(len(record["prompt"]) - len({hit["key"] for hit in record["prompt"]})
                                                                  for record, _, _ in records.values()),
                                   "rawToHydratedLosses": sum(len(record["raw"]) - len(record["hydrated"]) for record, _, _ in records.values()),
                                   "hydratedToPromptLosses": sum(len(record["hydrated"]) - len(record["prompt"]) for record, _, _ in records.values())},
            "labelDisagreements": {"public": pub_labels["agreement"], "independent": ind_frozen["agreement"],
                                   "production": "Prior point evidence review, no pairwise disagreement estimate",
                                   "status": "AI provisional. Original assessor decisions remain stored with their labels. Independent set is now a diagnostic regression set."},
            "spend": {"thisReplayEmbeddingHttp": 0, "thisReplayDeepSeekHttp": 0, "thisReplayEstimatedCny": "0",
                      "beforeD3EEmbeddingHttp": historical_usage["embeddingHttpAttempts"],
                      "beforeD3EDeepSeekHttp": historical_usage["deepseekModelHttpAttempts"],
                      "beforeD3EHistoricalEstimateCny": historical_usage["totalEstimatedCny"],
                      "vendorBillReconciled": historical_usage["vendorBillReconciled"]},
            "groups": groups, "queries": rows_by_group}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, default=ROOT / "backups/D3_BCD_20261114")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = run_replay(args.run)
    queries = result.pop("queries")
    with args.output.open("x", encoding="utf-8") as stream:
        header = json.dumps(result, ensure_ascii=False, allow_nan=False, indent=2)
        stream.write(header[:-2] + ',\n  "queries": {\n')
        for index, (group, rows) in enumerate(queries.items()):
            stream.write(f"    {json.dumps(group)}: [\n")
            stream.write(",\n".join("      " + json.dumps(row, ensure_ascii=False, allow_nan=False, separators=(",", ":"))
                                    for row in rows))
            stream.write("\n    ]" + (",\n" if index + 1 < len(queries) else "\n"))
        stream.write("  }\n}\n")


if __name__ == "__main__":
    main()
