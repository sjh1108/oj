package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.Problem;
import jakarta.validation.constraints.*;

import java.util.List;

public record UpdateProblemRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        String inputDescription,
        String outputDescription,
        @NotNull @Min(100) @Max(60000) Integer timeLimit,
        @NotNull @Min(1024) @Max(1048576) Integer memoryLimit,
        @NotNull Problem.Difficulty difficulty,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
        @NotNull Boolean isPublic
) {
}
