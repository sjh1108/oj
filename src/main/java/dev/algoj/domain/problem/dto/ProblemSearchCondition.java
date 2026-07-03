package dev.algoj.domain.problem.dto;

import dev.algoj.domain.problem.entity.Problem;

/** Optional filters for the problem list. Null/blank fields mean "no filter". */
public record ProblemSearchCondition(
        String keyword,
        Problem.Difficulty difficulty,
        String tag,
        SolvedFilter solved
) {
    /** Filter by the requesting user's judging history. */
    public enum SolvedFilter {
        ALL,
        /** Has at least one ACCEPTED submission. */
        SOLVED,
        /** Has submissions but none ACCEPTED. */
        ATTEMPTED,
        /** No ACCEPTED submission (never tried, or tried and failed). */
        UNSOLVED
    }

    public SolvedFilter solvedOrAll() {
        return solved != null ? solved : SolvedFilter.ALL;
    }
}
