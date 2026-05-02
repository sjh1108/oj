package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.TestCase;

public record TestCaseResponse(
        Long id,
        String input,
        String expectedOutput,
        Integer orderIndex,
        Boolean isSample
) {
    public static TestCaseResponse from(TestCase tc) {
        return new TestCaseResponse(
                tc.getId(),
                tc.getInput(),
                tc.getExpectedOutput(),
                tc.getOrderIndex(),
                tc.getIsSample()
        );
    }
}
