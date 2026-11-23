# Toyota Technical Intelligence (TTI) — Phase 1

Phase 1 builds a **metadata-only catalog** from the Toyota Manuals index:

- Source: https://toyotamanuals.gitlab.io/toyotamanuals/
- Captures model, model code, engine codes, production range, region, language, manual ID, manual format, and source/download links.
- Writes normalized JSON and CSV catalog files.
- Does **not** download, mirror, parse, or redistribute manual contents.

## Why metadata first

The index contains multiple document formats (including HTML/XML web manuals, PDF, CD, and custom entries). The metadata catalog lets OBD2P identify candidate references by vehicle profile before any document-specific integration is attempted.

A matching model name is not enough to select a manual. The application must compare model code, engine, production range, region, and language, and must retain multiple candidates when the evidence is ambiguous.

## Run

From the repository root:

```bash
python -m pip install -r tools/toyota_tti/requirements.txt
python tools/toyota_tti/catalog_crawler.py --out build/toyota-tti
```

Output:

- `build/toyota-tti/toyota_manual_catalog.json`
- `build/toyota-tti/toyota_manual_catalog.csv`
- `build/toyota-tti/catalog_summary.json`

Use a different index with `--url` if required. Set a reasonable network timeout with `--timeout`.

## Data integrity

Each record includes a stable SHA-256-derived record ID, source URL, and capture timestamp. The catalog also records a SHA-256 fingerprint of the fetched index HTML. Missing values remain empty; the crawler does not infer years, engines, regions, or compatibility.

The crawler is intentionally conservative: if the source table layout is not recognized, it fails with a useful error instead of silently emitting a misleading catalog.

## Next phase

Phase 2 can inspect selected documents **one at a time**, after source access and reuse permissions have been checked. Parsed technical facts must carry document ID, section/page, source URL, and extraction status. Do not use this metadata catalog as a repair instruction or as proof that a manual applies to a particular vehicle.

## Rights and source limitations

Public accessibility does not establish permission to copy, store, redistribute, or commercially use manual contents. This phase stores index metadata and links only. Review the source terms and rights for each document before adding document content to any distributable product.


## Phase 2 — Local manual evidence extraction

Phase 2 adds a **single-file, local-only parser**. It supports HTML/HTM, XML, text, and text-based PDF files. It does not fetch the URL supplied in `--source-url`, crawl a manual, or download linked pages.

Install dependencies:

```bash
python -m pip install -r tools/toyota_tti/requirements.txt
```

Example (the input file must already be lawfully available locally):

```bash
python tools/toyota_tti/manual_parser.py \
  --input ./authorized-manual/sample.html \
  --out build/toyota-tti/evidence-N00107.json \
  --manual-id N00107 \
  --source-url "https://example.invalid/reference" \
  --model Hilux --model-code GUN126 --engine 1GD-FTV \
  --year 2020 --region Thailand --language English \
  --rights-note "Access and intended processing reviewed for this local copy."
```

The parser records a SHA-256 fingerprint, manual ID, optional vehicle context, extraction timestamp, section/page locators, headings, and short excerpts for candidate DTC/measurement references. Candidate evidence is explicitly marked `candidate_unverified`; it is not a diagnosis, specification approval, repair instruction, or confirmation that the document applies to the connected vehicle.

PDF pages without embedded text are reported as having no extractable text; OCR is not performed. Wiring diagrams and images are not interpreted in this phase. Review each extracted candidate against the complete original page and the exact vehicle application before it is used by OBD2P.

The `--rights-note` is an operator-supplied audit note, not a legal determination or a permission grant. The parser processes one local file per invocation and does not automate acquisition of manuals.
