package dev.algoj.global.client;

import dev.algoj.domain.submission.entity.Submission.Status;

public final class Judge0StatusMapper {

    private Judge0StatusMapper() {}

    public static Status toSubmissionStatus(int judge0StatusId) {
        return switch (judge0StatusId) {
            case 1 -> Status.PENDING;
            case 2 -> Status.JUDGING;
            case 3 -> Status.ACCEPTED;
            case 4 -> Status.WRONG_ANSWER;
            case 5 -> Status.TIME_LIMIT;
            case 6 -> Status.COMPILE_ERROR;
            case 7, 8, 9, 10, 11, 12 -> Status.RUNTIME_ERROR;
            default -> Status.SYSTEM_ERROR;
        };
    }
}
