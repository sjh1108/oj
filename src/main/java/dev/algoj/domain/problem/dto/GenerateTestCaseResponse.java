package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.TestCase;

/**
 * Lightweight response for a generated test case. Intentionally does NOT return the full
 * input/expectedOutput (which can be large) — only sizes and a short preview — so large
 * data never travels back to the browser.
 */
public record GenerateTestCaseResponse(
        Long id,
        Integer orderIndex,
        Boolean isSample,
        int inputSize,
        int outputSize,
        String inputPreview,
        String outputPreview,
        Integer generatorRuntimeMs,
        Integer solutionRuntimeMs,
        // Null when no validator code was sent.
        Integer validatorRuntimeMs
) {
    private static final int PREVIEW_LENGTH = 500;

    public static GenerateTestCaseResponse from(TestCase tc,
                                                Integer generatorRuntimeMs,
                                                Integer solutionRuntimeMs,
                                                Integer validatorRuntimeMs) {
        return new GenerateTestCaseResponse(
                tc.getId(),
                tc.getOrderIndex(),
                tc.getIsSample(),
                tc.getInput().length(),
                tc.getExpectedOutput().length(),
                preview(tc.getInput()),
                preview(tc.getExpectedOutput()),
                generatorRuntimeMs,
                solutionRuntimeMs,
                validatorRuntimeMs
        );
    }

    private static String preview(String s) {
        if (s == null) return "";
        return s.length() > PREVIEW_LENGTH ? s.substring(0, PREVIEW_LENGTH) : s;
    }
}
