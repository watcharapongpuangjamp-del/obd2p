#!/usr/bin/env python3
"""Extract traceable candidate evidence from one locally supplied manual.

No network access, document crawling, or bulk manual download is performed.
Extracted values are candidates only; this tool never creates repair advice.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

from bs4 import BeautifulSoup

SUPPORTED_SUFFIXES = {".html", ".htm", ".xml", ".pdf", ".txt"}
DTC_RE = re.compile(r"(?<![A-Z0-9])([PCBU][0-9][0-9A-F]{3})(?![A-Z0-9])", re.IGNORECASE)
UNIT_RE = re.compile(
    r"(?<![A-Za-z])(?:[-+]?\d+(?:\.\d+)?\s*(?:kPa|MPa|bar|psi|V|mV|A|mA|"
    r"ohm|Ω|N\s*[·.]?\s*m|Nm|mm|cm|°C|degC|rpm|Hz|%))(?![A-Za-z])",
    re.IGNORECASE,
)
HEADING_TAGS = {"h1", "h2", "h3", "h4", "h5", "h6"}
MAX_EXCERPT_CHARS = 280


def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _html_blocks(raw: bytes) -> tuple[list[dict], list[dict]]:
    soup = BeautifulSoup(raw, "html.parser")
    for node in soup(["script", "style", "noscript"]):
        node.decompose()
    blocks: list[dict] = []
    headings: list[dict] = []
    current_heading = ""
    index = 0
    for node in soup.find_all(list(HEADING_TAGS) + ["p", "li", "td", "th", "pre"]):
        value = normalize(node.get_text(" ", strip=True))
        if not value:
            continue
        if node.name in HEADING_TAGS:
            current_heading = value
            headings.append({"title": value, "locator": node.get("id") or f"heading:{len(headings) + 1}"})
            continue
        locator = node.get("id") or f"block:{index + 1}"
        blocks.append({"text": value, "locator": locator, "section": current_heading})
        index += 1
    if not blocks:
        body = normalize(soup.get_text(" ", strip=True))
        if body:
            blocks.append({"text": body, "locator": "document:body", "section": ""})
    return blocks, headings


def _xml_blocks(raw: bytes) -> tuple[list[dict], list[dict]]:
    root = ET.fromstring(raw)
    blocks: list[dict] = []
    headings: list[dict] = []
    def walk(element: ET.Element, path: str, section: str = "") -> None:
        tag = element.tag.rsplit("}", 1)[-1]
        current = section
        text = normalize(" ".join(element.itertext()))
        if tag.lower() in HEADING_TAGS or tag.lower() in {"title", "heading", "caption"}:
            if text:
                current = text
                headings.append({"title": text, "locator": path})
        elif text and len(list(element)) == 0:
            blocks.append({"text": text, "locator": path, "section": current})
        counts: dict[str, int] = {}
        for child in list(element):
            child_tag = child.tag.rsplit("}", 1)[-1]
            counts[child_tag] = counts.get(child_tag, 0) + 1
            walk(child, f"{path}/{child_tag}[{counts[child_tag]}]", current)
    root_tag = root.tag.rsplit("}", 1)[-1]
    walk(root, f"/{root_tag}[1]")
    return blocks, headings


def _pdf_blocks(raw: bytes) -> tuple[list[dict], list[dict]]:
    try:
        from pypdf import PdfReader
    except ImportError as exc:
        raise RuntimeError("PDF support requires pypdf; install tools/toyota_tti/requirements.txt") from exc
    import io
    reader = PdfReader(io.BytesIO(raw), strict=False)
    blocks: list[dict] = []
    for page_number, page in enumerate(reader.pages, start=1):
        text = normalize(page.extract_text() or "")
        if text:
            blocks.append({"text": text, "locator": f"pdf:page:{page_number}", "section": ""})
    return blocks, []


def extract_blocks(path: Path, raw: bytes) -> tuple[list[dict], list[dict]]:
    suffix = path.suffix.lower()
    if suffix in {".html", ".htm"}:
        return _html_blocks(raw)
    if suffix == ".xml":
        return _xml_blocks(raw)
    if suffix == ".pdf":
        return _pdf_blocks(raw)
    if suffix == ".txt":
        text = normalize(raw.decode("utf-8-sig", errors="replace"))
        return ([{"text": text, "locator": "text:document", "section": ""}] if text else []), []
    raise ValueError(f"Unsupported file type: {suffix or '(no extension)'}")


def _snippet(text: str, start: int, end: int) -> str:
    left = max(0, start - 100)
    right = min(len(text), end + 100)
    return normalize(text[left:right])[:MAX_EXCERPT_CHARS]


def extract_evidence(blocks: list[dict]) -> list[dict]:
    evidence: list[dict] = []
    seen: set[tuple[str, str, str]] = set()
    for block in blocks:
        text = block["text"]
        common = {"locator": block["locator"], "section": block.get("section", "")}
        for match in DTC_RE.finditer(text):
            code = match.group(1).upper()
            key = ("dtc_candidate", code, block["locator"])
            if key in seen:
                continue
            seen.add(key)
            evidence.append({
                **common, "kind": "dtc_candidate", "value": code,
                "excerpt": _snippet(text, match.start(), match.end()),
                "extraction_status": "candidate_unverified",
            })
        for match in UNIT_RE.finditer(text):
            value = normalize(match.group(0))
            key = ("measurement_candidate", value, block["locator"])
            if key in seen:
                continue
            seen.add(key)
            evidence.append({
                **common, "kind": "measurement_candidate", "value": value,
                "excerpt": _snippet(text, match.start(), match.end()),
                "extraction_status": "candidate_unverified",
            })
    return evidence


def parse_manual(
    path: Path,
    manual_id: str,
    source_url: str,
    vehicle_context: dict,
    rights_note: str,
) -> dict:
    if not manual_id.strip():
        raise ValueError("manual_id must not be empty")
    if not rights_note.strip():
        raise ValueError("rights_note must not be empty")
    if not path.is_file():
        raise FileNotFoundError(f"Input document does not exist: {path}")
    if path.suffix.lower() not in SUPPORTED_SUFFIXES:
        raise ValueError(f"Supported formats: {', '.join(sorted(SUPPORTED_SUFFIXES))}")
    raw = path.read_bytes()
    blocks, headings = extract_blocks(path, raw)
    evidence = extract_evidence(blocks)
    extracted_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    return {
        "schema_version": "1.0.0",
        "document": {
            "manual_id": manual_id,
            "source_url": source_url,
            "local_filename": path.name,
            "format": path.suffix.lower().lstrip("."),
            "sha256": hashlib.sha256(raw).hexdigest(),
            "byte_length": len(raw),
            "extracted_at": extracted_at,
            "rights_review": {"status": "user_attested_reviewed", "note": rights_note},
        },
        "vehicle_context": vehicle_context,
        "extraction": {
            "status": "text_extracted" if blocks else "no_extractable_text",
            "block_count": len(blocks),
            "heading_count": len(headings),
            "evidence_count": len(evidence),
            "limitations": [
                "Evidence is machine-extracted and has not been verified against the vehicle.",
                "A DTC occurrence does not establish applicability, cause, or repair procedure.",
                "Measurements are literal candidates; units, conditions, ranges, and test states require manual review.",
                "Scanned PDF pages without an embedded text layer require a separate OCR review.",
            ],
        },
        "headings": headings,
        "evidence": evidence,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, help="One local HTML, XML, PDF, or TXT file")
    parser.add_argument("--out", required=True, help="Output JSON path")
    parser.add_argument("--manual-id", required=True, help="Manual ID from the catalog")
    parser.add_argument("--source-url", default="", help="Reference URL; does not trigger a network request")
    parser.add_argument("--model", default="")
    parser.add_argument("--model-code", default="")
    parser.add_argument("--engine", default="")
    parser.add_argument("--year", default="")
    parser.add_argument("--region", default="")
    parser.add_argument("--language", default="")
    parser.add_argument("--rights-note", required=True, help="Brief note describing the rights/access review performed")
    args = parser.parse_args()

    context = {
        "model": args.model, "model_code": args.model_code, "engine": args.engine,
        "year": args.year, "region": args.region, "language": args.language,
    }
    try:
        result = parse_manual(Path(args.input), args.manual_id, args.source_url, context, args.rights_note)
        output = Path(args.out)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print(f"Parsed one local document: {result['extraction']['status']}")
    print(f"Evidence candidates: {result['extraction']['evidence_count']}")
    print(f"Output: {Path(args.out).resolve()}")
    print("No remote document was fetched and no repair advice was generated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
