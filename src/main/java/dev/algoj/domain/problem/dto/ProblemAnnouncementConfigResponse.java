package dev.algoj.domain.problem.dto;

/** Whether the "선정 문제 알림" button can be used, and which role it mentions. */
public record ProblemAnnouncementConfigResponse(boolean enabled, String roleId) {
}
