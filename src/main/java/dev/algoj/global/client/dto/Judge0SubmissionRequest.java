package dev.algoj.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Judge0SubmissionRequest(
        String sourceCode,
        int languageId,
        String stdin,
        String expectedOutput,
        Double cpuTimeLimit,
        Integer memoryLimit,
        // KB. Judge0's default fsize is 1024KB, too small for multi-MB
        // generated test data; bounded above by Judge0's MAX_MAX_FILE_SIZE.
        Integer maxFileSize
) {
    public static Judge0SubmissionRequest of(
            String sourceCode,
            int languageId,
            String stdin,
            String expectedOutput,
            int timeLimitMs,
            int memoryLimitKb,
            Integer maxFileSizeKb) {
        return new Judge0SubmissionRequest(
                sourceCode,
                languageId,
                stdin,
                expectedOutput,
                timeLimitMs / 1000.0,
                memoryLimitKb,
                maxFileSizeKb
        );
    }
}
