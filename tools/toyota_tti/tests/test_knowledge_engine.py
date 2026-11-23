import unittest

from tools.toyota_tti.knowledge_engine import (
    assess_applicability, catalog_candidates, retrieve_evidence, validate_evidence
)


def evidence_doc(context=None, status="text_extracted"):
    return {
        "schema_version": "1.0.0",
        "document": {
            "manual_id": "N00107", "source_url": "https://example.test/manual",
            "sha256": "a" * 64,
            "rights_review": {"status": "user_attested_reviewed", "note": "Test fixture reviewed."}
        },
        "vehicle_context": context or {"model": "Hilux", "model_code": "GUN126", "engine": "1GD-FTV", "year": "2020", "region": "Thailand"},
        "extraction": {"status": status},
        "evidence": [{
            "locator": "pdf:page:12", "section": "Fuel System", "kind": "dtc_candidate",
            "value": "P0087", "excerpt": "Candidate occurrence P0087", "extraction_status": "candidate_unverified"
        }]
    }


class KnowledgeEngineTests(unittest.TestCase):
    def test_exact_vehicle_context_is_candidate_match(self):
        result = assess_applicability(
            evidence_doc()["vehicle_context"],
            {"model_code": "GUN126", "engine": "1GD-FTV", "year": "2020"}
        )
        self.assertEqual(result["status"], "candidate_match")

    def test_engine_mismatch_excludes_evidence(self):
        result = retrieve_evidence([evidence_doc()], {"model_code": "GUN126", "engine": "2GD-FTV", "year": "2020"})
        self.assertEqual(result, [])

    def test_missing_context_remains_unconfirmed(self):
        result = assess_applicability({"model": "Hilux"}, {"model_code": "GUN126", "engine": "1GD-FTV", "year": "2020"})
        self.assertEqual(result["status"], "unconfirmed")
        self.assertIn("engine", result["unknown_fields"])

    def test_retrieval_preserves_manual_provenance_and_candidate_status(self):
        result = retrieve_evidence([evidence_doc()], {"model_code": "GUN126", "engine": "1GD-FTV", "year": "2020"}, value="p0087")
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["manual_id"], "N00107")
        self.assertEqual(result[0]["document_sha256"], "a" * 64)
        self.assertEqual(result[0]["extraction_status"], "candidate_unverified")

    def test_rights_review_note_is_required(self):
        doc = evidence_doc()
        doc["document"]["rights_review"]["note"] = " "
        with self.assertRaisesRegex(ValueError, "rights review"):
            validate_evidence(doc)

    def test_no_text_document_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "no extractable text"):
            validate_evidence(evidence_doc(status="no_extractable_text"))

    def test_catalog_keeps_ambiguous_records_without_inventing_match(self):
        catalog = {"records": [
            {"manual_id": "A", "model_code": "GUN126", "engine_codes": ["1GD-FTV"], "production_start": "2019", "production_end": "2021", "regions": ["Thailand"]},
            {"manual_id": "B", "model_code": "GUN126", "engine_codes": ["2GD-FTV"], "production_start": "2019", "production_end": "2021", "regions": ["Thailand"]}
        ]}
        candidates = catalog_candidates(catalog, {"model_code": "GUN126", "engine": "1GD-FTV", "year": "2020", "region": "Thailand"})
        self.assertEqual(len(candidates), 2)  # catalog schema is not normalized to evidence context; do not infer
        self.assertTrue(all(item["applicability"]["status"] == "unconfirmed" for item in candidates))


if __ ().__class__ is None:
    pass

if __name__ == "__main__":
    unittest.main()
