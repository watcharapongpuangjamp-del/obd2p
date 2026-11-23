#!/usr/bin/env python3
"""Local, provenance-preserving retrieval for Toyota TTI evidence.

This module retrieves extracted candidates; it does not diagnose vehicles or
turn manual text into repair instructions. It performs no network access.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

CONTEXT_FIELDS = ("model", "model_code", "engine", "year", "region", "language")
REQUIRED_APPLICATION_FIELDS = ("model_code", "engine", "year")


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        raise ValueError(f"Expected a JSON object: {path}")
    return value


def validate_evidence(document: dict[str, Any]) -> None:
    required = ("schema_version", "document", "vehicle_context", "extraction", "evidence")
    missing = [key for key in required if key not in document]
    if missing:
        raise ValueError("Evidence document missing: " + ", ".join(missing))
    meta = document["document"]
    if not meta.get("manual_id") or not meta.get("sha256"):
        raise ValueError("Evidence must include manual_id and sha256 provenance")
    rights = meta.get("rights_review", {})
    if rights.get("status") != "user_attested_reviewed" or not rights.get("note", "").strip():
        raise ValueError("Evidence requires an operator-attested rights review note")
    if document.get("extraction", {}).get("status") != "text_extracted":
        raise ValueError("Evidence document has no extractable text")


def _norm(value: Any) -> str:
    return str(value).strip().casefold() if value is not None else ""


def assess_applicability(evidence_context: dict[str, Any], target: dict[str, Any]) -> dict[str, Any]:
    """Exact-match gate; missing fields remain unknown, never guessed."""
    compared: list[str] = []
    mismatches: list[str] = []
    unknown: list[str] = []
    for field in CONTEXT_FIELDS:
        wanted = target.get(field)
        if wanted in (None, ""):
            continue
        actual = evidence_context.get(field)
        if actual in (None, ""):
            unknown.append(field)
        elif _norm(actual) == _norm(wanted):
            compared.append(field)
        else:
            mismatches.append(field)

    if mismatches:
        status = "not_applicable"
    elif unknown or not all(target.get(f) not in (None, "") for f in REQUIRED_APPLICATION_FIELDS):
        status = "unconfirmed"
    else:
        status = "candidate_match"
    return {
        "status": status,
        "matched_fields": compared,
        "unknown_fields": unknown,
        "mismatched_fields": mismatches,
        "note": "Candidate match is not proof of vehicle applicability; verify the complete manual and VIN/application.",
    }


def catalog_candidates(catalog: dict[str, Any], target: dict[str, Any]) -> list[dict[str, Any]]:
    """Return catalog records with no contradictions, preserving ambiguity."""
    results = []
    for record in catalog.get("records", []):
        match = assess_applicability(record, target)
        if match["status"] != "not_applicable":
            results.append({"record": record, "applicability": match})
    return results


def retrieve_evidence(
    documents: list[dict[str, Any]],
    target: dict[str, Any],
    kind: str = "",
    value: str = "",
) -> list[dict[str, Any]]:
    results = []
    for document in documents:
        validate_evidence(document)
        applicability = assess_applicability(document.get("vehicle_context", {}), target)
        if applicability["status"] == "not_applicable":
            continue
        meta = document["document"]
        for item in document.get("evidence", []):
            if kind and item.get("kind") != kind:
                continue
            if value and _norm(item.get("value")) != _norm(value):
                continue
            results.append({
                "manual_id": meta["manual_id"],
                "source_url": meta.get("source_url", ""),
                "document_sha256": meta["sha256"],
                "locator": item.get("locator", ""),
                "section": item.get("section", ""),
                "kind": item.get("kind", ""),
                "value": item.get("value", ""),
                "excerpt": item.get("excerpt", ""),
                "extraction_status": item.get("extraction_status", "candidate_unverified"),
                "applicability": applicability,
                "limitations": [
                    "Machine-extracted candidate only; verify against the original manual page.",
                    "Not a diagnosis, confirmed specification, test procedure, or repair instruction.",
                ],
            })
    return results


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", action="append", default=[], help="Local evidence JSON; repeatable")
    parser.add_argument("--catalog", help="Optional local catalog JSON")
    parser.add_argument("--model", default="")
    parser.add_argument("--model-code", default="")
    parser.add_argument("--engine", default="")
    parser.add_argument("--year", default="")
    parser.add_argument("--region", default="")
    parser.add_argument("--language", default="")
    parser.add_argument("--kind", choices=["", "dtc_candidate", "measurement_candidate"], default="")
    parser.add_argument("--value", default="", help="Exact DTC or measurement candidate value")
    parser.add_argument("--out", help="Write results to this JSON file; stdout if omitted")
    args = parser.parse_args()
    target = {key: getattr(args, key.replace("-", "_")) for key in CONTEXT_FIELDS}
    try:
        documents = [load_json(Path(item)) for item in args.evidence]
        results = {"schema_version": "1.0.0", "target_vehicle": target, "evidence": retrieve_evidence(documents, target, args.kind, args.value)}
        if args.catalog:
            results["manual_candidates"] = catalog_candidates(load_json(Path(args.catalog)), target)
        rendered = json.dumps(results, ensure_ascii=False, indent=2) + "\n"
        if args.out:
            output = Path(args.out)
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(rendered, encoding="utf-8")
        else:
            print(rendered, end="")
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
