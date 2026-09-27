package dev.algoj.domain.problem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The message exactly as the admin edited it (2000 is Discord's length limit),
 * the week it announces, and whether to record it. A test send is not
 * recorded, so it doesn't count toward the next notice's default week.
 */
public record ProblemAnnouncementRequest(
        @NotBlank @Size(max = 2000) String content,
        @Min(2000) @Max(2100) int year,
        @Min(1) @Max(12) int month,
        @Min(1) @Max(6) int weekOfMonth,
        boolean record
) {
}
