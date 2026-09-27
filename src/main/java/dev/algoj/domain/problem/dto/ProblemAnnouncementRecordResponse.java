package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.ProblemAnnouncement;

import java.time.LocalDateTime;

public record ProblemAnnouncementRecordResponse(
        Long id, int year, int month, int weekOfMonth, LocalDateTime createdAt) {

    public static ProblemAnnouncementRecordResponse from(ProblemAnnouncement a) {
        return new ProblemAnnouncementRecordResponse(
                a.getId(), a.getYear(), a.getMonth(), a.getWeekOfMonth(), a.getCreatedAt());
    }
}
