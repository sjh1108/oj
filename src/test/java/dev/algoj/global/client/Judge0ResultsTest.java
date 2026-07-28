package dev.algoj.global.client;

import dev.algoj.global.client.dto.Judge0SubmissionResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Judge0ResultsTest {

    private static final int WRONG_ANSWER = 4;
    private static final int COMPILE_ERROR = 6;
    private static final int RUNTIME_ERROR_NZEC = 11;

    /** javac emits this for `List<Integer>[] a = new List[n];` — a warning, not an error. */
    private static final String JAVAC_NOTE = """
            Note: Main.java uses unchecked or unsafe operations.
            Note: Recompile with -Xlint:unchecked for details.
            """;

    private Judge0SubmissionResponse response(int statusId, String stderr, String compileOutput, String message) {
        return new Judge0SubmissionResponse(
                "out", stderr, compileOutput, message, "0.01", 1000, "tok",
                new Judge0SubmissionResponse.Status(statusId, "desc"));
    }

    @Test
    void compilerWarnings_areNotAnErrorMessage() {
        var res = response(WRONG_ANSWER, null, JAVAC_NOTE, null);

        assertThat(Judge0Results.pickErrorMessage(res)).isNull();
    }

    @Test
    void compileError_usesCompileOutput() {
        String error = "Main.java:3: error: ';' expected";
        var res = response(COMPILE_ERROR, null, error, null);

        assertThat(Judge0Results.pickErrorMessage(res)).isEqualTo(error);
    }

    @Test
    void runtimeError_prefersStderrOverCompilerWarnings() {
        var res = response(RUNTIME_ERROR_NZEC, "Exception in thread \"main\"", JAVAC_NOTE, null);

        assertThat(Judge0Results.pickErrorMessage(res)).isEqualTo("Exception in thread \"main\"");
    }

    @Test
    void message_isUsedWhenNothingElseIsAvailable() {
        var res = response(RUNTIME_ERROR_NZEC, null, null, "Killed");

        assertThat(Judge0Results.pickErrorMessage(res)).isEqualTo("Killed");
    }

    @Test
    void compileWarning_isExposedOnlyWhenCompilationSucceeded() {
        assertThat(Judge0Results.pickCompileWarning(response(WRONG_ANSWER, null, JAVAC_NOTE, null)))
                .isEqualTo(JAVAC_NOTE);
        assertThat(Judge0Results.pickCompileWarning(response(COMPILE_ERROR, null, "error: ';' expected", null)))
                .isNull();
        assertThat(Judge0Results.pickCompileWarning(response(WRONG_ANSWER, null, "  ", null)))
                .isNull();
    }
}
