package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.ProblemNoteResponse;
import dev.algoj.domain.problem.dto.UpdateProblemNoteRequest;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.ProblemNote;
import dev.algoj.domain.problem.repository.ProblemNoteRepository;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemNoteServiceTest {

    @Mock
    ProblemNoteRepository noteRepository;
    @Mock
    ProblemRepository problemRepository;
    @Mock
    UserRepository userRepository;

    @InjectMocks
    ProblemNoteService service;

    @Test
    void get_withoutRow_returnsEmpty() {
        givenProblem();
        when(noteRepository.findByUserIdAndProblemId(1L, 7L)).thenReturn(Optional.empty());

        ProblemNoteResponse response = service.get(1L, 7L);

        assertThat(response.content()).isNull();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void get_withRow_returnsContent() {
        givenProblem();
        when(noteRepository.findByUserIdAndProblemId(1L, 7L))
                .thenReturn(Optional.of(note("N ≤ 20 이라 비트마스크")));

        assertThat(service.get(1L, 7L).content()).isEqualTo("N ≤ 20 이라 비트마스크");
    }

    @Test
    void update_withoutRow_createsOne() {
        givenProblem();
        when(noteRepository.findByUserIdAndProblemId(1L, 7L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(noteRepository.save(any(ProblemNote.class))).thenAnswer(inv -> inv.getArgument(0));

        ProblemNoteResponse response =
                service.update(1L, 7L, new UpdateProblemNoteRequest("dp[mask][k]"));

        assertThat(response.content()).isEqualTo("dp[mask][k]");
    }

    @Test
    void update_withRow_updatesInPlaceWithoutLoadingUser() {
        givenProblem();
        ProblemNote existing = note("옛 메모");
        when(noteRepository.findByUserIdAndProblemId(1L, 7L)).thenReturn(Optional.of(existing));
        when(noteRepository.save(existing)).thenReturn(existing);

        assertThat(service.update(1L, 7L, new UpdateProblemNoteRequest("새 메모")).content())
                .isEqualTo("새 메모");
        assertThat(existing.getContent()).isEqualTo("새 메모");
        verify(userRepository, never()).findById(any());
    }

    // 빈 메모를 행으로 남기면 "메모 없음"과 "빈 메모"가 갈린다 — 지워서 하나로 만든다.
    @Test
    void update_withBlankContent_deletesRow() {
        givenProblem();
        ProblemNote existing = note("지울 메모");
        when(noteRepository.findByUserIdAndProblemId(1L, 7L)).thenReturn(Optional.of(existing));

        ProblemNoteResponse response =
                service.update(1L, 7L, new UpdateProblemNoteRequest("   \n  "));

        assertThat(response.content()).isNull();
        verify(noteRepository).delete(existing);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void update_withNullContent_deletesRow() {
        givenProblem();
        when(noteRepository.findByUserIdAndProblemId(1L, 7L)).thenReturn(Optional.empty());

        assertThat(service.update(1L, 7L, new UpdateProblemNoteRequest(null)).content()).isNull();
        verify(noteRepository, never()).save(any());
    }

    @Test
    void update_trimsSurroundingWhitespace() {
        givenProblem();
        when(noteRepository.findByUserIdAndProblemId(1L, 7L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(noteRepository.save(any(ProblemNote.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(1L, 7L, new UpdateProblemNoteRequest("  메모  \n")).content())
                .isEqualTo("메모");
    }

    @Test
    void withUnknownProblem_throws() {
        when(problemRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);
    }

    // 정답 스냅샷은 메모가 없으면 null이어야 한다 — 빈 문자열이 제출에 붙으면
    // 프론트가 "메모 있음"으로 오해한다.
    @Test
    void contentForSnapshot_withoutRow_returnsNull() {
        when(noteRepository.findByUserIdAndProblemId(1L, 7L)).thenReturn(Optional.empty());

        assertThat(service.contentForSnapshot(1L, 7L)).isNull();
    }

    @Test
    void contentForSnapshot_withRow_returnsContent() {
        when(noteRepository.findByUserIdAndProblemId(1L, 7L))
                .thenReturn(Optional.of(note("정답 당시 메모")));

        assertThat(service.contentForSnapshot(1L, 7L)).isEqualTo("정답 당시 메모");
    }

    private void givenProblem() {
        lenient().when(problemRepository.findById(7L)).thenReturn(Optional.of(problem()));
    }

    private ProblemNote note(String content) {
        return ProblemNote.of(user(), problem(), content);
    }

    private Problem problem() {
        Problem p = Problem.builder()
                .title("잠수정 두 대의 승선 배치")
                .description("본문")
                .difficulty(Problem.Difficulty.SILVER)
                .timeLimit(3000)
                .memoryLimit(524288)
                .build();
        ReflectionTestUtils.setField(p, "id", 7L);
        return p;
    }

    private User user() {
        return User.builder()
                .username("tester")
                .email("tester@example.com")
                .password("HASH")
                .role(User.Role.USER)
                .build();
    }
}
