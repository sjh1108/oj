package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.CreateProblemRequest;
import dev.algoj.domain.problem.dto.ProblemDetailResponse;
import dev.algoj.domain.problem.dto.ProblemListResponse;
import dev.algoj.domain.problem.dto.ProblemSearchCondition;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.submission.repository.SubmissionRepository;
import dev.algoj.domain.user.entity.User;
import dev.algoj.domain.user.repository.UserRepository;
import dev.algoj.global.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    ProblemRepository problemRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    SubmissionRepository submissionRepository;

    @InjectMocks
    ProblemService service;

    @Test
    void normalizeTags_trimsAndDropsBlanks() {
        assertThat(ProblemService.normalizeTags(List.of(" DP ", "", "  ", "그래프")))
                .containsExactly("DP", "그래프");
    }

    @Test
    void normalizeTags_dedupesCaseInsensitivelyKeepingFirstSpelling() {
        assertThat(ProblemService.normalizeTags(List.of("DP", "dp", "Dp", "수학")))
                .containsExactly("DP", "수학");
    }

    @Test
    void normalizeTags_nullMeansNoTags() {
        assertThat(ProblemService.normalizeTags(null)).isEmpty();
    }

    @Test
    void create_storesNormalizedTags() {
        User author = User.builder()
                .username("admin").email("a@a.dev").password("pw").role(User.Role.ADMIN)
                .build();
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(problemRepository.save(any(Problem.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateProblemRequest req = new CreateProblemRequest(
                "두 수의 합", "설명", null, null,
                1000, 262144, Problem.Difficulty.BRONZE,
                List.of(" DP ", "dp", "그래프"),
                true, null, null);

        ProblemDetailResponse res = service.create(req, principal);

        assertThat(res.tags()).containsExactlyInAnyOrder("DP", "그래프");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_flagsSolvedAndAttemptedProblemsForTheUser() {
        Problem solved = problem(1L);
        Problem attempted = problem(2L);
        Problem untouched = problem(3L);
        Page<Problem> page = new PageImpl<>(List.of(solved, attempted, untouched));
        when(problemRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(page);
        when(submissionRepository.findDistinctSolvedProblemIdsByUserId(7L))
                .thenReturn(List.of(1L));
        when(submissionRepository.findDistinctSubmittedProblemIdsByUserId(7L))
                .thenReturn(List.of(1L, 2L));

        ProblemSearchCondition cond = new ProblemSearchCondition(null, null, null, null);
        List<ProblemListResponse> content =
                service.list(cond, 7L, false, PageRequest.of(0, 20)).getContent();

        assertThat(content).extracting(ProblemListResponse::solved)
                .containsExactly(true, false, false);
        assertThat(content).extracting(ProblemListResponse::attempted)
                .containsExactly(false, true, false);
    }

    private Problem problem(Long id) {
        Problem p = Problem.builder()
                .title("p" + id).description("d")
                .timeLimit(1000).memoryLimit(262144)
                .difficulty(Problem.Difficulty.BRONZE)
                .tags(Set.of())
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
