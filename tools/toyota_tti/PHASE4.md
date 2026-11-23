# Toyota Diagnostic Intelligence — Phase 4

Phase 4 adds an isolated Kotlin correlation layer for OBD2P. It combines existing DTC records, fresh validated generic OBD telemetry, and locally supplied Toyota manual evidence. It does not alter the live scanner, send commands, or generate repair instructions.

## Correlation policy

- Only telemetry with quality `VALID`, provenance `REAL_HARDWARE` or `SIMULATOR`, finite values, and age within the configured freshness window is eligible.
- AI-inferred, estimated, user-entered, historical, stale, unsupported, invalid, and unavailable values are excluded.
- Initial PID map:
  - P0171/P0172 → PID 06 and 07 (Bank 1 fuel trims)
  - P0101 → PID 10 (MAF) and 04 (calculated engine load)
  - P0115/P0117/P0118/P0128 → PID 05 (coolant temperature)
  - P0087 has no mapped standard PID in this implementation. Do not substitute an unrelated value for Toyota common-rail pressure.
- Output reports evidence coverage (DTC-only or DTC plus eligible telemetry), not diagnostic confidence or probability. Manual candidates do not increase certainty.
- Manual candidates are accepted only with a non-empty manual ID, valid SHA-256 fingerprint, and `candidate_unverified` extraction status.
- Toyota manual evidence is matched against model code, engine code, production-year range, and region. Missing fields stay UNCONFIRMED; known contradictions are excluded.
- CANDIDATE_MATCH means metadata agrees, not that the manual is verified for the VIN or that extracted content is correct.

## Scope boundaries

This is a pure correlation component with no UI or live diagnostic integration in this phase. It does not infer root cause, decide a component is defective, establish OEM limits, recommend a repair, or run bidirectional tests. Verify every manual excerpt against the full original document and exact vehicle application before using it in a service decision.

## Tests

Unit tests cover telemetry freshness/provenance gates, DTC-to-PID mapping, conservative P0087 handling, and Toyota manual applicability filtering.
