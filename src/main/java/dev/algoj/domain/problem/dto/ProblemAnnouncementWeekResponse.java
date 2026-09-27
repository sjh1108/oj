package dev.algoj.domain.problem.dto;

import java.time.LocalDate;

/** "10월 1주차" for a study date — the nth announced study of that month. */
public record ProblemAnnouncementWeekResponse(LocalDate studyDate, int month, int weekOfMonth) {
}
