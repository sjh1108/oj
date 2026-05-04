package dev.algoj.domain.run.dto;

public record RunResponse(
        String status,
        String stdout,
        String stderr,
        String compileOutput,
        Integer runtimeMs,
        Integer memoryKb,
        String errorMessage
) {
}
