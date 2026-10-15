import unittest

from replay import content_rule_candidates, f32


class ContentRuleCandidateTest(unittest.TestCase):
    def test_empty_text(self):
        self.assertEqual(content_rule_candidates(" \n\t"), ["EMPTY_TEXT"])

    def test_references_without_body(self):
        self.assertEqual(content_rule_candidates(
            "- [TX] Spring 6.2 transaction docs\n[TX]: https://example.test/tx"),
            ["REFERENCE_ONLY_OR_METADATA_REVIEW"])

    def test_reference_continuation_and_metadata(self):
        self.assertEqual(content_rule_candidates(
            "MySQL 8.0 ACID\n- [TX] Transaction docs\n[TX]: https://example.test/tx\n项目类比：路径说明"),
            ["REFERENCE_ONLY_OR_METADATA_REVIEW"])

    def test_link_with_substantive_explanation_keeps_body(self):
        self.assertEqual(content_rule_candidates(
            "代理调用才能触发事务拦截。\nchecked 异常默认不回滚。\n[TX]: https://example.test/tx"), [])

    def test_short_valid_content_has_no_length_cutoff(self):
        self.assertEqual(content_rule_candidates("CAS 是比较并交换。"), [])

    def test_single_explanation_with_reference_list_is_not_reference_only(self):
        self.assertEqual(content_rule_candidates(
            "代理调用才能触发事务拦截。\n- [TX] Spring docs\n[TX]: https://example.test/tx"), [])

    def test_balanced_code(self):
        self.assertEqual(content_rule_candidates("```java\ncount++;\n```"), [])

    def test_code_continuation_requires_neighbor_review(self):
        self.assertEqual(content_rule_candidates("count++;\n```\n这段代码需结合前块理解。"),
                         ["UNBALANCED_FENCE_REVIEW_WITH_NEIGHBOR"])

    def test_float32_cutoff_is_inclusive(self):
        self.assertGreaterEqual(f32("0.70"), f32(0.70))
        self.assertLess(f32("0.6999999"), f32(0.70))


if __name__ == "__main__":
    unittest.main()
