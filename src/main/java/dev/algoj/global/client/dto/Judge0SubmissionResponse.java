package dev.algoj.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Judge0SubmissionResponse(
        String stdout,
        String stderr,
        @JsonProperty("compile_output") String compileOutput,
        String message,
        String time,
        Integer memory,
        String token,
        Status status
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(int id, String description) {}

    public Integer runtimeMs() {
        if (time == null || time.isBlank()) return null;
        try {
            return (int) Math.round(Double.parseDouble(time) * 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
