package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.ProblemAnnouncement;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProblemAnnouncementRecordResponse(
        Long id, LocalDate studyDate, int weekOfMonth, LocalDateTime createdAt) {

    public static ProblemAnnouncementRecordResponse from(ProblemAnnouncement a) {
        return new ProblemAnnouncementRecordResponse(
                a.getId(), a.getStudyDate(), a.getWeekOfMonth(), a.getCreatedAt());
    }
}
