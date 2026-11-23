#!/usr/bin/env python3
"""Build a metadata-only catalog from the Toyota Manuals index.

This tool deliberately does not download linked manual documents.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable
from urllib.parse import urljoin

from bs4 import BeautifulSoup

DEFAULT_URL = "https://toyotamanuals.gitlab.io/toyotamanuals/"
USER_AGENT = "OBD2P-Toyota-TTI-MetadataCatalog/0.1 (+metadata-only)"
KNOWN_FORMATS = {"CD", "PDF", "HTMLWEB", "XMLWEB", "WEB", "CUSTOM"}
FIELDS = [
    "record_id", "model", "model_code", "engine_codes", "production_start",
    "production_end", "regions", "language", "manual_id", "manual_format",
    "manual_url", "download_url", "source_url", "captured_at",
]


def cell_lines(cell) -> list[str]:
    """Return visible cell text as non-empty lines, preserving <br> boundaries."""
    if cell is None:
        return []
    for br in cell.find_all("br"):
        br.replace_with("\n")
    text = cell.get_text("\n", strip=True)
    return [re.sub(r"\s+", " ", line).strip() for line in text.splitlines() if line.strip()]


def split_values(cell) -> list[str]:
    return cell_lines(cell)


def parse_years(lines: list[str]) -> tuple[str, str]:
    years = [line for line in lines if re.fullmatch(r"(?:19|20)\d{2}/(?:\d{2}|\?{2})", line)]
    return (years[0] if years else "", years[1] if len(years) > 1 else "")


def extract_manual(cell) -> tuple[str, str, str]:
    if cell is None:
        return "", "", ""
    anchors = cell.find_all("a", href=True)
    manual_anchor = anchors[0] if anchors else None
    manual_id = manual_anchor.get_text(" ", strip=True) if manual_anchor else ""
    href = urljoin(DEFAULT_URL, manual_anchor["href"]) if manual_anchor else ""
    lines = cell_lines(cell)
    format_name = ""
    for line in lines:
        if line.upper() in KNOWN_FORMATS:
            format_name = line
            break
    if not manual_id:
        for line in lines:
            if re.fullmatch(r"[A-Z0-9][A-Z0-9_-]{3,}", line):
                manual_id = line
                break
    return manual_id, format_name, href


def stable_record_id(record: dict) -> str:
    keys = ("model", "model_code", "engine_codes", "production_start",
            "production_end", "regions", "language", "manual_id", "manual_format")
    canonical = json.dumps({key: record.get(key, "") for key in keys},
                           ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:20]


def parse_index_html(html: str, source_url: str, captured_at: str) -> list[dict]:
    soup = BeautifulSoup(html, "html.parser")
    table = None
    for candidate in soup.find_all("table"):
        header_text = " ".join(candidate.stripped_strings).lower()
        if "model" in header_text and "manual" in header_text and "engine" in header_text:
            table = candidate
            break
    if table is None:
        raise ValueError("Could not find the Toyota index table (expected Model, Engines, and Manual columns).")

    records: list[dict] = []
    for row in table.find_all("tr"):
        cells = row.find_all(["td", "th"], recursive=False)
        if not cells or any(cell.name == "th" for cell in cells):
            continue
        values = [cell_lines(cell) for cell in cells]
        if len(cells) < 8:
            continue

        # The published index may expose Year as one cell or as Start/End subcolumns.
        if len(cells) >= 9:
            model_i, code_i, engine_i, start_i, end_i, region_i, lang_i, manual_i, download_i = range(9)
            model = " ".join(values[model_i])
            model_code = " ".join(values[code_i])
            engines = values[engine_i]
            start = values[start_i][0] if values[start_i] else ""
            end = values[end_i][0] if values[end_i] else ""
            regions = values[region_i]
            language = " ".join(values[lang_i])
        else:
            model_i, code_i, engine_i, year_i, region_i, lang_i, manual_i, download_i = range(8)
            model = " ".join(values[model_i])
            model_code = " ".join(values[code_i])
            engines = values[engine_i]
            start, end = parse_years(values[year_i])
            regions = values[region_i]
            language = " ".join(values[lang_i])

        manual_id, manual_format, manual_url = extract_manual(cells[manual_i])
        download_anchors = cells[download_i].find_all("a", href=True)
        download_url = urljoin(source_url, download_anchors[0]["href"]) if download_anchors else ""

        # Ignore non-data rows and preserve unknown/missing fields as empty values.
        if not model or not (manual_id or manual_url):
            continue

        record = {
            "model": model,
            "model_code": model_code,
            "engine_codes": engines,
            "production_start": start,
            "production_end": end,
            "regions": regions,
            "language": language,
            "manual_id": manual_id,
            "manual_format": manual_format,
            "manual_url": manual_url,
            "download_url": download_url,
            "source_url": source_url,
            "captured_at": captured_at,
        }
        record["record_id"] = stable_record_id(record)
        records.append(record)

    if not records:
        raise ValueError("The index table was found, but no manual records were parsed. Review the source table layout.")
    return records


def fetch_html(url: str, timeout: float) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        content_type = response.headers.get("Content-Type", "")
        if "text/html" not in content_type.lower() and "application/xhtml" not in content_type.lower():
            raise ValueError(f"Expected HTML response; received Content-Type: {content_type or 'unknown'}")
        return response.read()


def write_outputs(records: list[dict], html_bytes: bytes, out_dir: Path, source_url: str, captured_at: str) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    json_path = out_dir / "toyota_manual_catalog.json"
    csv_path = out_dir / "toyota_manual_catalog.csv"
    summary_path = out_dir / "catalog_summary.json"

    payload = {
        "schema_version": "1.0.0",
        "source": source_url,
        "captured_at": captured_at,
        "source_html_sha256": hashlib.sha256(html_bytes).hexdigest(),
        "record_count": len(records),
        "records": records,
    }
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with csv_path.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS, extrasaction="ignore")
        writer.writeheader()
        for record in records:
            row = dict(record)
            for key in ("engine_codes", "regions"):
                row[key] = " | ".join(row.get(key, []))
            writer.writerow(row)

    summary = {
        "schema_version": "1.0.0",
        "source": source_url,
        "captured_at": captured_at,
        "source_html_sha256": payload["source_html_sha256"],
        "record_count": len(records),
        "models": sorted({record["model"] for record in records}),
        "manual_formats": dict(sorted(_count(record["manual_format"] or "UNKNOWN" for record in records).items())),
        "regions": dict(sorted(_count(region for record in records for region in record["regions"]).items())),
        "note": "Metadata and links only; no linked manual documents were downloaded.",
    }
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _count(items: Iterable[str]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for item in items:
        counts[item] = counts.get(item, 0) + 1
    return counts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default=DEFAULT_URL, help="Toyota Manuals index URL")
    parser.add_argument("--out", default="build/toyota-tti", help="Output directory")
    parser.add_argument("--timeout", type=float, default=20.0, help="HTTP timeout in seconds")
    args = parser.parse_args()

    if args.timeout <= 0:
        parser.error("--timeout must be greater than zero")

    captured_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    try:
        html_bytes = fetch_html(args.url, args.timeout)
        html = html_bytes.decode("utf-8", errors="replace")
        records = parse_index_html(html, args.url, captured_at)
        write_outputs(records, html_bytes, Path(args.out), args.url, captured_at)
    except Exception as exc:  # CLI boundary: emit a concise actionable error.
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print(f"Catalog created: {len(records)} metadata records")
    print(f"Output directory: {Path(args.out).resolve()}")
    print("Linked manuals were not downloaded.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
