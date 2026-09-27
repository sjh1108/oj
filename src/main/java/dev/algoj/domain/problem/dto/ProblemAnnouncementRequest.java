package dev.algoj.domain.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * The message exactly as the admin edited it (2000 is Discord's length limit),
 * the study it announces, and whether to record it. A test send is not recorded,
 * so it doesn't count toward the next notice's week number.
 */
public record ProblemAnnouncementRequest(
        @NotBlank @Size(max = 2000) String content,
        @NotNull LocalDate studyDate,
        boolean record
) {
}
