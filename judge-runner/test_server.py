"""Tests for the parts that decide a verdict.

The sandbox itself needs a docker daemon, but everything that turns its output
into a Judge0 status is pure — and it is also where a mistake silently marks a
correct solution wrong. Run with:  python3 -m unittest discover judge-runner
"""

import unittest

from server import (
    classify, normalize_output,
    ST_ACCEPTED, ST_WRONG_ANSWER, ST_TIME_LIMIT, ST_COMPILE_ERROR,
    ST_SIGSEGV, ST_SIGXFSZ, ST_NZEC, ST_INTERNAL,
)


def meta_ok(exit_code=0, cpu="0.010", cpu_limit="2", maxrss="2048"):
    return {"outcome": "ok", "exit_code": str(exit_code), "cpu": cpu,
            "cpu_limit": cpu_limit, "maxrss_kb": maxrss}


class NormalizeOutputTest(unittest.TestCase):
    def test_trailing_newline_is_ignored(self):
        self.assertEqual(normalize_output("42\n"), normalize_output("42"))

    def test_trailing_spaces_per_line_are_ignored(self):
        self.assertEqual(normalize_output("1 2  \n3\t\n"), normalize_output("1 2\n3"))

    def test_crlf_matches_lf(self):
        self.assertEqual(normalize_output("a\r\nb\r\n"), normalize_output("a\nb\n"))

    def test_interior_blank_lines_are_kept(self):
        self.assertNotEqual(normalize_output("a\n\nb"), normalize_output("a\nb"))

    def test_leading_whitespace_is_significant(self):
        self.assertNotEqual(normalize_output(" a"), normalize_output("a"))


class ClassifyTest(unittest.TestCase):
    def test_matching_output_is_accepted(self):
        status, _ = classify(meta_ok(), False, 0, "3\n", "3\n")
        self.assertEqual(status, ST_ACCEPTED)

    def test_output_differing_only_in_trailing_whitespace_is_accepted(self):
        status, _ = classify(meta_ok(), False, 0, "3", "3  \n\n")
        self.assertEqual(status, ST_ACCEPTED)

    def test_different_output_is_wrong_answer(self):
        status, _ = classify(meta_ok(), False, 0, "3\n", "4\n")
        self.assertEqual(status, ST_WRONG_ANSWER)

    def test_no_expected_output_means_accepted(self):
        # The run flow and the test-case generator submit without an expected
        # answer and treat 3/4 as "it ran" (Judge0Results.ranSuccessfully).
        status, _ = classify(meta_ok(), False, 0, None, "anything")
        self.assertEqual(status, ST_ACCEPTED)

    def test_compile_error_wins_over_everything(self):
        status, _ = classify({"outcome": "compile_error"}, False, 0, "3", "")
        self.assertEqual(status, ST_COMPILE_ERROR)

    def test_timeout_exit_code(self):
        status, _ = classify(meta_ok(exit_code=124), False, 124, "3", "")
        self.assertEqual(status, ST_TIME_LIMIT)

    def test_sigkill_after_timeout_is_a_timeout(self):
        status, _ = classify(meta_ok(exit_code=137), False, 137, "3", "")
        self.assertEqual(status, ST_TIME_LIMIT)

    def test_cpu_budget_exhausted_is_a_timeout_even_on_a_clean_exit(self):
        status, _ = classify(meta_ok(exit_code=0, cpu="2.000", cpu_limit="2"),
                             False, 0, "3", "3")
        self.assertEqual(status, ST_TIME_LIMIT)

    def test_oom_outranks_the_exit_code(self):
        status, message = classify(meta_ok(exit_code=137), True, 137, "3", "")
        self.assertEqual(status, ST_SIGSEGV)
        self.assertIn("memory", message)

    def test_segfault(self):
        status, _ = classify(meta_ok(exit_code=139), False, 139, "3", "")
        self.assertEqual(status, ST_SIGSEGV)

    def test_output_size_limit(self):
        status, _ = classify(meta_ok(exit_code=153), False, 153, "3", "")
        self.assertEqual(status, ST_SIGXFSZ)

    def test_nonzero_exit_is_nzec(self):
        status, message = classify(meta_ok(exit_code=1), False, 1, "3", "")
        self.assertEqual(status, ST_NZEC)
        self.assertIn("1", message)

    def test_missing_meta_is_an_internal_error(self):
        status, _ = classify({}, False, 125, "3", "")
        self.assertEqual(status, ST_INTERNAL)

    def test_missing_meta_with_oom_is_reported_as_memory(self):
        # The container can be killed before the script writes anything.
        status, message = classify({}, True, 137, "3", "")
        self.assertEqual(status, ST_SIGSEGV)
        self.assertIn("memory", message)

    def test_unsupported_language(self):
        status, _ = classify({"outcome": "unsupported_language"}, False, 0, None, "")
        self.assertEqual(status, ST_INTERNAL)


if __name__ == "__main__":
    unittest.main()
