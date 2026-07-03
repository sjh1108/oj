package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.submission.entity.Submission;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specification fragments for the problem search. Each factory returns null
 * when its filter is inactive, so callers can collect and combine only the
 * active ones. Submission-based filters use EXISTS subqueries (no joins), so
 * pagination never needs DISTINCT.
 */
public final class ProblemSpecs {

    private ProblemSpecs() {
    }

    /** Non-admins only see public problems. */
    public static Specification<Problem> visibleTo(boolean isAdmin) {
        return isAdmin ? null : (root, query, cb) -> cb.isTrue(root.get("isPublic"));
    }

    public static Specification<Problem> titleContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern);
    }

    public static Specification<Problem> hasDifficulty(Problem.Difficulty difficulty) {
        return difficulty == null
                ? null
                : (root, query, cb) -> cb.equal(root.get("difficulty"), difficulty);
    }

    public static Specification<Problem> hasTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String trimmed = tag.trim();
        return (root, query, cb) -> cb.isMember(trimmed, root.get("tags"));
    }

    /** Has at least one ACCEPTED submission by the user. */
    public static Specification<Problem> solvedBy(Long userId) {
        return (root, query, cb) ->
                submissionExists(root, query, cb, userId, Submission.Status.ACCEPTED);
    }

    /** No ACCEPTED submission by the user. */
    public static Specification<Problem> notSolvedBy(Long userId) {
        return (root, query, cb) ->
                cb.not(submissionExists(root, query, cb, userId, Submission.Status.ACCEPTED));
    }

    /** Submitted but never ACCEPTED. */
    public static Specification<Problem> attemptedBy(Long userId) {
        return (root, query, cb) -> cb.and(
                submissionExists(root, query, cb, userId, null),
                cb.not(submissionExists(root, query, cb, userId, Submission.Status.ACCEPTED)));
    }

    private static Predicate submissionExists(Root<Problem> root,
                                              CriteriaQuery<?> query,
                                              CriteriaBuilder cb,
                                              Long userId,
                                              Submission.Status status) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<Submission> s = sub.from(Submission.class);
        sub.select(cb.literal(1L));
        Predicate sameProblem = cb.equal(s.get("problem"), root);
        Predicate sameUser = cb.equal(s.get("user").get("id"), userId);
        sub.where(status == null
                ? cb.and(sameProblem, sameUser)
                : cb.and(sameProblem, sameUser, cb.equal(s.get("status"), status)));
        return cb.exists(sub);
    }
}
