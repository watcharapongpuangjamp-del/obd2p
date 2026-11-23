import unittest

from tools.toyota_tti.catalog_crawler import parse_index_html


SAMPLE = """
<html><body><table>
<tr><th>Model</th><th>Model Code</th><th>Engines</th><th>Year</th><th>Region</th><th>Language</th><th>Manual</th><th>Download</th></tr>
<tr>
<td>Hilux</td><td>GUN1##<br>GUN156</td><td>1GD-FTV<br>2GD-FTV</td>
<td>2017/03<br>2022/01</td><td>Thailand<br>Australia</td><td>English</td>
<td><a href="https://example.test/manual/N00150">N00150</a><br>Web</td>
<td><a href="https://example.test/download/N00150">Download</a></td>
</tr>
</table></body></html>
"""


class CatalogCrawlerTests(unittest.TestCase):
    def test_parses_metadata_without_fetching_manual(self):
        records = parse_index_html(SAMPLE, "https://example.test/index/", "2026-11-23T00:00:00+00:00")
        self.assertEqual(len(records), 1)
        record = records[0]
        self.assertEqual(record["model"], "Hilux")
        self.assertEqual(record["engine_codes"], ["1GD-FTV", "2GD-FTV"])
        self.assertEqual(record["production_start"], "2017/03")
        self.assertEqual(record["production_end"], "2022/01")
        self.assertEqual(record["regions"], ["Thailand", "Australia"])
        self.assertEqual(record["manual_id"], "N00150")
        self.assertEqual(record["manual_format"], "Web")
        self.assertTrue(record["manual_url"].endswith("/manual/N00150"))
        self.assertTrue(record["download_url"].endswith("/download/N00150"))
        self.assertEqual(len(record["record_id"]), 20)

    def test_fails_when_index_table_missing(self):
        with self.assertRaises(ValueError):
            parse_index_html("<html><body>No table</body></html>", "https://example.test/", "now")


NINE_COLUMN_SAMPLE = """
<html><body><table>
<tr><th>Model</th><th>Model Code</th><th>Engines</th><th>Year Start</th><th>Year End</th><th>Region</th><th>Language</th><th>Manual</th><th>Download</th></tr>
<tr>
<td>Avanza</td><td>F65#</td><td>K3-VE<br>3SZ-VE</td><td>2011/11</td><td>2017/09</td><td>Thailand<br>Malaysia</td><td>English</td>
<td><a href="manual/N00014">N00014</a><br>Web</td>
<td><a href="download/N00014">Download</a></td>
</tr>
</table></body></html>
"""


class NineColumnLayoutTests(unittest.TestCase):
    def test_parses_split_year_columns_and_relative_links(self):
        records = parse_index_html(NINE_COLUMN_SAMPLE, "https://example.test/toyota/", "2026-11-23T00:00:00+00:00")
        self.assertEqual(len(records), 1)
        record = records[0]
        self.assertEqual(record["production_start"], "2011/11")
        self.assertEqual(record["production_end"], "2017/09")
        self.assertEqual(record["manual_url"], "https://example.test/toyota/manual/N00014")
        self.assertEqual(record["download_url"], "https://example.test/toyota/download/N00014")


if __name__ == "__main__":
    unittest.main()
