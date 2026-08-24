package dev.algoj.domain.submission.dto;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제출에 붙은 메모 사본이 누구에게 실리는지.
 *
 * 이 화면은 이미 "공개 + 정답 + 보는 사람도 그 문제를 해결"일 때만 남에게 열린다
 * (SubmissionService.detail). 여기서는 그 위에 얹히는 메모 자체의 공개 여부만 본다.
 */
class SubmissionNoteVisibilityTest {

    private Submission accepted(String note, boolean notePublic) {
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
                .totalTestCases(1)
                .build();
        s.updateResult(Submission.Status.ACCEPTED, null, null, null);
        if (note != null) s.attachNoteSnapshot(note, notePublic);
        return s;
    }

    @Test
    void owner_seesOwnNote_evenWhenPrivate() {
        Submission s = accepted("비공개 메모", false);

        assertThat(SubmissionDetailResponse.from(s, true).noteSnapshot()).isEqualTo("비공개 메모");
    }

    @Test
    void other_cannotSeePrivateNote() {
        Submission s = accepted("비공개 메모", false);

        assertThat(SubmissionDetailResponse.from(s, false).noteSnapshot()).isNull();
    }

    @Test
    void other_seesPublicNote() {
        Submission s = accepted("공개 메모", true);

        assertThat(SubmissionDetailResponse.from(s, false).noteSnapshot()).isEqualTo("공개 메모");
    }

    @Test
    void withoutNote_bothSeeNothing() {
        Submission s = accepted(null, false);

        assertThat(SubmissionDetailResponse.from(s, true).noteSnapshot()).isNull();
        assertThat(SubmissionDetailResponse.from(s, false).noteSnapshot()).isNull();
        assertThat(SubmissionDetailResponse.from(s, true).noteSnapshotPublic()).isFalse();
    }
}
