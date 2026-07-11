package dev.algoj.domain.submission.dto;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** BOJ-style: runtime/memory are exposed only for ACCEPTED submissions. */
class SubmissionResponsePerfVisibilityTest {

    private Submission submissionWith(Submission.Status status) {
        Problem problem = Problem.builder()
                .title("t").description("d")
                .timeLimit(1000).memoryLimit(256000)
                .difficulty(Problem.Difficulty.BRONZE)
                .isPublic(true)
                .build();
        User user = User.builder()
                .username("u").email("u@e.com").password("p")
                .role(User.Role.USER)
                .build();
        Submission s = Submission.builder()
                .problem(problem)
                .user(user)
                .sourceCode("code")
                .language(Submission.Language.PYTHON3)
                .status(Submission.Status.PENDING)
                .totalTestCases(2)
                .build();
        s.incrementPassed(120, 4096); // passed-prefix numbers exist either way
        s.updateResult(status, null, null, null);
        return s;
    }

    @Test
    void judging_exposesPercentProgress_notCounts() {
        Submission s = submissionWith(Submission.Status.JUDGING);
        // 1 of 2 cases passed → 50%
        assertThat(SubmissionResponse.from(s).progress()).isEqualTo(50);
        assertThat(SubmissionDetailResponse.from(s).progress()).isEqualTo(50);
    }

    @Test
    void finishedStatuses_haveNullProgress() {
        assertThat(SubmissionResponse.from(submissionWith(Submission.Status.ACCEPTED)).progress()).isNull();
        assertThat(SubmissionResponse.from(submissionWith(Submission.Status.WRONG_ANSWER)).progress()).isNull();
    }

    @Test
    void accepted_exposesRuntimeAndMemory() {
        Submission s = submissionWith(Submission.Status.ACCEPTED);
        assertThat(SubmissionResponse.from(s).runtime()).isEqualTo(120);
        assertThat(SubmissionResponse.from(s).memory()).isEqualTo(4096);
        assertThat(SubmissionDetailResponse.from(s).runtime()).isEqualTo(120);
        assertThat(SubmissionDetailResponse.from(s).memory()).isEqualTo(4096);
    }

    @Test
    void nonAccepted_hidesRuntimeAndMemory() {
        for (Submission.Status st : new Submission.Status[]{
                Submission.Status.WRONG_ANSWER,
                Submission.Status.TIME_LIMIT,
                Submission.Status.MEMORY_LIMIT,
                Submission.Status.RUNTIME_ERROR,
                Submission.Status.PARTIAL,
                Submission.Status.JUDGING}) {
            Submission s = submissionWith(st);
            assertThat(SubmissionResponse.from(s).runtime()).as("runtime for %s", st).isNull();
            assertThat(SubmissionResponse.from(s).memory()).as("memory for %s", st).isNull();
            assertThat(SubmissionDetailResponse.from(s).runtime()).as("detail runtime for %s", st).isNull();
            assertThat(SubmissionDetailResponse.from(s).memory()).as("detail memory for %s", st).isNull();
        }
    }
}
