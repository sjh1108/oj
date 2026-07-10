package dev.algoj.domain.problem.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * One chunk of a chunked test-case upload. Chunks must stay well under the
 * reverse proxy's 1MB body limit — the max below is a defensive cap, clients
 * should send ~384K chars per chunk.
 */
public record AppendTestCaseChunkRequest(
        @Size(max = 786_432, message = "inputChunk가 너무 큽니다.")
        String inputChunk,

        @Size(max = 786_432, message = "expectedOutputChunk가 너무 큽니다.")
        String expectedOutputChunk
) {
    @AssertTrue(message = "inputChunk 또는 expectedOutputChunk 중 하나는 필요합니다.")
    public boolean isAtLeastOneChunkPresent() {
        return inputChunk != null || expectedOutputChunk != null;
    }
}
