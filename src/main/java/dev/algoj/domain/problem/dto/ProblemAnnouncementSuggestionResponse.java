package dev.algoj.domain.problem.dto;

/** Default week for the month's next notice — one past the highest already sent. */
public record ProblemAnnouncementSuggestionResponse(int year, int month, int weekOfMonth) {
}
