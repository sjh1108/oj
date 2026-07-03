package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the search Specifications against a real (in-memory, MySQL-mode)
 * database, since predicate/subquery bugs don't show up in mock-based tests.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:algoj;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "algoj.seed-admin=false",
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProblemRepositorySearchTest {

    @Autowired
    ProblemRepository problemRepository;

    @Autowired
    TestEntityManager em;

    User solver;
    Problem dp;        // public BRONZE, tags [DP, 수학] — solver ACCEPTED
    Problem graph;     // public GOLD, tags [그래프]    — solver WRONG_ANSWER
    Problem hidden;    // private SILVER, tags [DP]     — untouched

    @BeforeEach
    void seed() {
        User author = em.persist(user("author"));
        solver = em.persist(user("solver"));

        dp = em.persist(problem(author, "다이나믹 두 수의 합", Problem.Difficulty.BRONZE,
                List.of("DP", "수학"), true));
        graph = em.persist(problem(author, "최단 경로", Problem.Difficulty.GOLD,
                List.of("그래프"), true));
        hidden = em.persist(problem(author, "비공개 문제", Problem.Difficulty.SILVER,
                List.of("DP"), false));

        em.persist(submission(solver, dp, Submission.Status.ACCEPTED));
        em.persist(submission(solver, dp, Submission.Status.WRONG_ANSWER));
        em.persist(submission(solver, graph, Submission.Status.WRONG_ANSWER));
        em.flush();
        em.clear();
    }

    @Test
    void visibleTo_nonAdminExcludesPrivateProblems() {
        List<Problem> found = problemRepository.findAll(spec(ProblemSpecs.visibleTo(false)));
        assertThat(found).extracting(Problem::getId)
                .containsExactlyInAnyOrder(dp.getId(), graph.getId());
    }

    @Test
    void titleContains_matchesSubstring() {
        List<Problem> found = problemRepository.findAll(spec(ProblemSpecs.titleContains("경로")));
        assertThat(found).extracting(Problem::getId).containsExactly(graph.getId());
    }

    @Test
    void hasDifficulty_filtersExactTier() {
        List<Problem> found = problemRepository.findAll(
                spec(ProblemSpecs.hasDifficulty(Problem.Difficulty.GOLD)));
        assertThat(found).extracting(Problem::getId).containsExactly(graph.getId());
    }

    @Test
    void hasTag_matchesElementCollection() {
        List<Problem> found = problemRepository.findAll(spec(ProblemSpecs.hasTag("DP")));
        assertThat(found).extracting(Problem::getId)
                .containsExactlyInAnyOrder(dp.getId(), hidden.getId());
    }

    @Test
    void solvedBy_needsAnAcceptedSubmission() {
        List<Problem> found = problemRepository.findAll(
                spec(ProblemSpecs.solvedBy(solver.getId())));
        assertThat(found).extracting(Problem::getId).containsExactly(dp.getId());
    }

    @Test
    void attemptedBy_meansSubmittedButNeverAccepted() {
        List<Problem> found = problemRepository.findAll(
                spec(ProblemSpecs.attemptedBy(solver.getId())));
        assertThat(found).extracting(Problem::getId).containsExactly(graph.getId());
    }

    @Test
    void notSolvedBy_includesFailedAndUntouchedProblems() {
        List<Problem> found = problemRepository.findAll(
                spec(ProblemSpecs.notSolvedBy(solver.getId())));
        assertThat(found).extracting(Problem::getId)
                .containsExactlyInAnyOrder(graph.getId(), hidden.getId());
    }

    @Test
    void combinedFilters_intersect() {
        List<Problem> found = problemRepository.findAll(
                Specification.allOf(
                        ProblemSpecs.visibleTo(false),
                        ProblemSpecs.hasTag("DP"),
                        ProblemSpecs.solvedBy(solver.getId())));
        assertThat(found).extracting(Problem::getId).containsExactly(dp.getId());
    }

    @Test
    void findAllTags_respectsVisibility() {
        assertThat(problemRepository.findAllTags(true))
                .containsExactly("DP", "그래프", "수학");
        // Non-admin: 'hidden' also carries DP, but DP survives via the public problem.
        assertThat(problemRepository.findAllTags(false))
                .containsExactly("DP", "그래프", "수학");
    }

    private static Specification<Problem> spec(Specification<Problem> s) {
        return s;
    }

    private static User user(String name) {
        return User.builder()
                .username(name)
                .email(name + "@algoj.dev")
                .password("pw")
                .role(User.Role.USER)
                .build();
    }

    private static Problem problem(User author, String title, Problem.Difficulty difficulty,
                                   List<String> tags, boolean isPublic) {
        return Problem.builder()
                .title(title)
                .description("desc")
                .timeLimit(1000)
                .memoryLimit(262144)
                .difficulty(difficulty)
                .tags(tags)
                .author(author)
                .isPublic(isPublic)
                .build();
    }

    private static Submission submission(User user, Problem problem, Submission.Status status) {
        return Submission.builder()
                .user(user)
                .problem(problem)
                .sourceCode("print()")
                .language(Submission.Language.PYTHON3)
                .status(status)
                .totalTestCases(1)
                .build();
    }
}
