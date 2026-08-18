package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The summary query is native (JPQL substring() rejects a CLOB column), so its
 * column aliases and the projection binding only hold up against a real database.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:algoj-tc;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "algoj.seed-admin=false",
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TestCaseRepositorySummaryTest {

    @Autowired
    TestCaseRepository testCaseRepository;

    @Autowired
    TestEntityManager em;

    Problem problem;

    @BeforeEach
    void seed() {
        User author = em.persist(User.builder()
                .username("author").email("author@algoj.dev").password("pw")
                .role(User.Role.ADMIN)
                .build());
        problem = em.persist(Problem.builder()
                .title("p").description("d")
                .timeLimit(1000).memoryLimit(262144)
                .difficulty(Problem.Difficulty.GOLD)
                .author(author)
                .isPublic(false)
                .build());
        em.persist(testCase(0, true, "small in", "small out", false));
        em.persist(testCase(1, false, "A".repeat(5000), "B".repeat(9000), false));
        em.persist(testCase(2, false, "draft", "draft", true));
        em.flush();
        em.clear();
    }

    @Test
    void summaries_carryFullLengthsButOnlyPreviewData() {
        List<TestCaseSummary> found =
                testCaseRepository.findSummariesByProblemId(problem.getId(), 100);

        assertThat(found).hasSize(3);
        TestCaseSummary big = found.get(1);
        assertThat(big.getInputLength()).isEqualTo(5000L);
        assertThat(big.getExpectedOutputLength()).isEqualTo(9000L);
        assertThat(big.getInputPreview()).hasSize(100);
        assertThat(big.getExpectedOutputPreview()).hasSize(100);
    }

    @Test
    void summaries_returnCasesInOrderWithFlags() {
        List<TestCaseSummary> found =
                testCaseRepository.findSummariesByProblemId(problem.getId(), 100);

        assertThat(found).extracting(TestCaseSummary::getOrderIndex).containsExactly(0, 1, 2);
        assertThat(found).extracting(TestCaseSummary::getIsSample).containsExactly(true, false, false);
        assertThat(found).extracting(TestCaseSummary::getIsDraft).containsExactly(false, false, true);
        // A case shorter than the preview window comes back whole.
        assertThat(found.get(0).getInputPreview()).isEqualTo("small in");
    }

    private TestCase testCase(int orderIndex, boolean isSample, String input, String output, boolean draft) {
        TestCase tc = TestCase.builder()
                .problem(problem)
                .input(input)
                .expectedOutput(output)
                .orderIndex(orderIndex)
                .isSample(isSample)
                .isDraft(draft)
                .build();
        return tc;
    }
}
