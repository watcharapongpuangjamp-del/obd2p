import tempfile
import unittest
from pathlib import Path

from tools.toyota_tti.manual_parser import parse_manual


class ManualParserTests(unittest.TestCase):
    def parse_fixture(self, suffix, content):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / f"sample{suffix}"
        path.write_bytes(content)
        return parse_manual(
            path=path,
            manual_id="N00107",
            source_url="https://example.test/manual",
            vehicle_context={"model": "Hilux", "engine": "1GD-FTV"},
            rights_note="Test fixture created for parser unit test.",
        )

    def test_html_extracts_dtc_and_measurement_as_unverified_candidates(self):
        result = self.parse_fixture(
            ".html",
            b"<html><h1>Fuel System</h1><p>Check DTC P0087. Rail pressure is 35 MPa at idle.</p></html>",
        )
        self.assertEqual(result["extraction"]["status"], "text_extracted")
        self.assertEqual(result["headings"][0]["title"], "Fuel System")
        kinds = {(item["kind"], item["value"], item["extraction_status"]) for item in result["evidence"]}
        self.assertIn(("dtc_candidate", "P0087", "candidate_unverified"), kinds)
        self.assertIn(("measurement_candidate", "35 MPa", "candidate_unverified"), kinds)
        self.assertIn("PDF", result["extraction"]["limitations"][3])

    def test_xml_extracts_text_with_element_locator(self):
        result = self.parse_fixture(
            ".xml",
            b"<manual><section><title>Engine Control</title><step>Stored code P0101</step></section></manual>",
        )
        self.assertTrue(any(item["value"] == "P0101" for item in result["evidence"]))
        self.assertTrue(result["evidence"][0]["locator"].startswith("/manual[1]"))

    def test_text_document_is_supported(self):
        result = self.parse_fixture(".txt", b"Check sensor circuit U0100.")
        self.assertTrue(any(item["value"] == "U0100" for item in result["evidence"]))

    def test_missing_file_is_rejected(self):
        with self.assertRaises(FileNotFoundError):
            parse_manual(
                Path("/path/that/does/not/exist.pdf"), "N00107", "", {}, "reviewed"
            )

    def test_unsupported_extension_is_rejected(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / "sample.docx"
        path.write_bytes(b"not supported")
        with self.assertRaises(ValueError):
            parse_manual(path, "N00107", "", {}, "reviewed")

    def test_empty_manual_id_is_rejected(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / "sample.txt"
        path.write_text("DTC P0100", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "manual_id"):
            parse_manual(path, " ", "", {}, "reviewed")

    def test_empty_rights_note_is_rejected(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / "sample.txt"
        path.write_text("DTC P0100", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "rights_note"):
            parse_manual(path, "N00107", "", {}, " ")


if __name__ == "__main__":
    unittest.main()
