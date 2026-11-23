# Toyota Technical Intelligence (TTI)

TTI is an isolated, local-first evidence layer for OBD2P. It must not be treated as a diagnostic authority.

## Phase 1 — Metadata catalog

Build a metadata-only index from the Toyota Manuals listing. The crawler records model/application metadata and links; it does not fetch manual contents.

## Phase 2 — Local manual evidence extraction

Parse one locally supplied HTML, XML, PDF text layer, or TXT file. Every extracted DTC or measurement is marked `candidate_unverified`, with document hash and locator. The operator's rights note is an audit note, not a legal permission grant.

## Phase 3 — Knowledge retrieval

The knowledge engine retrieves evidence from local JSON files and optionally filters by exact candidate value and evidence kind. It preserves manual ID, source URL, SHA-256, locator, excerpt, and extraction status.

Vehicle application is deliberately conservative:
- Contradictory known context excludes a record.
- Missing context is reported as `unconfirmed`.
- Evidence `candidate_match` requires exact model code, engine, and year in the extracted evidence context.
- Catalog matching uses model code, engine-code membership, production-year bounds, and region when supplied.
- Multiple catalog candidates remain multiple candidates; the engine does not select a manual by model name alone.
- Catalog records are not converted into repair facts.

Run tests:

```bash
python -m pip install -r tools/toyota_tti/requirements.txt
python -m unittest discover -s tools/toyota_tti/tests -t . -v
```

Retrieve from local evidence:

```bash
python tools/toyota_tti/knowledge_engine.py \\
  --evidence build/toyota-tti/evidence-N00107.json \\
  --model-code GUN126 --engine 1GD-FTV --year 2020 \\
  --kind dtc_candidate --value P0087
```

Optionally include a metadata catalog:

```bash
python tools/toyota_tti/knowledge_engine.py \\
  --evidence build/toyota-tti/evidence-N00107.json \\
  --catalog build/toyota-tti/toyota_manual_catalog.json \\
  --model-code GUN126 --engine 1GD-FTV --year 2020
```

This phase is retrieval only. It does not connect to the vehicle, send diagnostic requests, assert a confirmed fault, recommend repairs, or modify OBD2P's live diagnosis path. Verify every candidate against the complete source manual and exact VIN/application before any later integration.

## Source and reuse constraints

The public index is not a blanket license for manual contents. Keep source files local and only process documents whose access and intended use have been reviewed. Do not commit copyrighted manual contents or generated excerpts into the repository.
