package dev.algoj.domain.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The message exactly as the admin edited it. 2000 is Discord's message length limit. */
public record ProblemAnnouncementRequest(
        @NotBlank @Size(max = 2000) String content
) {
}
