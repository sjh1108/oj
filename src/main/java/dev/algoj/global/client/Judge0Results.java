package dev.algoj.global.client;

import dev.algoj.global.client.dto.Judge0SubmissionResponse;

/**
 * Shared helpers for interpreting a Judge0 execution result.
 * Reused by the run flow and the test-case generator flow.
 */
public final class Judge0Results {

    private static final int MAX_ERROR_LENGTH = 2000;

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

    /** Prefer compile output, then stderr, then Judge0 message. Truncated for safety. */
    public static String pickErrorMessage(Judge0SubmissionResponse res) {
        if (res.compileOutput() != null && !res.compileOutput().isBlank()) return truncate(res.compileOutput());
        if (res.stderr() != null && !res.stderr().isBlank()) return truncate(res.stderr());
        if (res.message() != null && !res.message().isBlank()) return truncate(res.message());
        return null;
    }

    private static String truncate(String s) {
        return s.length() > MAX_ERROR_LENGTH ? s.substring(0, MAX_ERROR_LENGTH) : s;
    }
}
