package dev.algoj.global.client;

import dev.algoj.global.client.dto.Judge0SubmissionResponse;

/**
 * Shared helpers for interpreting a Judge0 execution result.
 * Reused by the run flow and the test-case generator flow.
 */
public final class Judge0Results {

    private static final int MAX_ERROR_LENGTH = 2000;
    private static final int COMPILE_ERROR_STATUS = 6;

    private Judge0Results() {
    }

    /** Judge0 status 3 (Accepted) and 4 (Wrong Answer) both mean the program ran to completion. */
    public static boolean ranSuccessfully(Judge0SubmissionResponse res) {
        int id = res.status().id();
        return id == 3 || id == 4;
    }

    public static String mapStatus(int judge0StatusId) {
        return switch (judge0StatusId) {
            case 3, 4 -> "OK";
            case 5 -> "TIME_LIMIT";
            case 6 -> "COMPILE_ERROR";
            case 7, 8, 9, 10, 11, 12 -> "RUNTIME_ERROR";
            default -> "SYSTEM_ERROR";
        };
    }

    /** Judge0 status 6 — the compile step itself failed. */
    public static boolean isCompileError(Judge0SubmissionResponse res) {
        return res.status() != null && res.status().id() == COMPILE_ERROR_STATUS;
    }

    /**
     * Compile output only counts as an error when the compile actually failed:
     * Judge0 fills compile_output on a *successful* compile too, with warnings and
     * notes (javac's "uses unchecked or unsafe operations" being the common one).
     * Showing those as the error message made a plain wrong answer look like a
     * compile error. Then stderr, then Judge0 message. Truncated for safety.
     */
    public static String pickErrorMessage(Judge0SubmissionResponse res) {
        if (isCompileError(res) && notBlank(res.compileOutput())) return truncate(res.compileOutput());
        if (notBlank(res.stderr())) return truncate(res.stderr());
        if (notBlank(res.message())) return truncate(res.message());
        return null;
    }

    /** Compiler warnings/notes from a compile that succeeded — informational, not an error. */
    public static String pickCompileWarning(Judge0SubmissionResponse res) {
        if (isCompileError(res) || !notBlank(res.compileOutput())) return null;
        return truncate(res.compileOutput());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String truncate(String s) {
        return s.length() > MAX_ERROR_LENGTH ? s.substring(0, MAX_ERROR_LENGTH) : s;
    }
}
