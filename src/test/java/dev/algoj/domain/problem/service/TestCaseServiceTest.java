package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.AppendTestCaseChunkRequest;
import dev.algoj.domain.problem.dto.TestCaseRequest;
import dev.algoj.domain.problem.dto.TestCaseResponse;
import dev.algoj.domain.problem.dto.TestCaseUploadStatusResponse;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.problem.repository.TestCaseRepository;
import dev.algoj.domain.problem.repository.TestCaseUploadMeta;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceTest {

    @Mock
    ProblemRepository problemRepository;
    @Mock
    TestCaseRepository testCaseRepository;

    @InjectMocks
    TestCaseService service;

    private record Meta(Long getId, Long getProblemId, Boolean getIsDraft,
                        Long getInputLength, Long getExpectedOutputLength) implements TestCaseUploadMeta {
        @Override public Long getId() { return getId; }
        @Override public Long getProblemId() { return getProblemId; }
        @Override public Boolean getIsDraft() { return getIsDraft; }
        @Override public Long getInputLength() { return getInputLength; }
        @Override public Long getExpectedOutputLength() { return getExpectedOutputLength; }
    }

    private Problem sampleProblem() {
        return Problem.builder()
                .title("t").description("d")
                .timeLimit(2000).memoryLimit(256000)
                .difficulty(Problem.Difficulty.BRONZE)
                .isPublic(true)
                .build();
    }

    @Test
    void add_withDraftTrue_persistsDraftTestCase() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(sampleProblem()));
        when(testCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestCaseResponse res = service.add(1L, new TestCaseRequest("", "", 0, false, true));

        ArgumentCaptor<TestCase> saved = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository).save(saved.capture());
        assertThat(saved.getValue().getIsDraft()).isTrue();
        assertThat(res.isDraft()).isTrue();
    }

    @Test
    void add_withoutDraft_defaultsToNotDraft() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(sampleProblem()));
        when(testCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TestCaseResponse res = service.add(1L, new TestCaseRequest("in", "out", 0, false, null));

        assertThat(res.isDraft()).isFalse();
    }

    @Test
    void appendChunk_appendsBothFields_viaConcatUpdates() {
        when(testCaseRepository.findUploadMetaById(10L))
                .thenReturn(Optional.of(new Meta(10L, 1L, true, 5L, 3L)));

        TestCaseUploadStatusResponse res = service.appendChunk(
                1L, 10L, new AppendTestCaseChunkRequest("abc", "xyz"));

        verify(testCaseRepository).appendInput(10L, "abc");
        verify(testCaseRepository).appendExpectedOutput(10L, "xyz");
        verify(testCaseRepository, never()).save(any());
        assertThat(res.id()).isEqualTo(10L);
        assertThat(res.draft()).isTrue();
    }

    @Test
    void appendChunk_skipsMissingSide() {
        when(testCaseRepository.findUploadMetaById(10L))
                .thenReturn(Optional.of(new Meta(10L, 1L, true, 5L, 3L)));

        service.appendChunk(1L, 10L, new AppendTestCaseChunkRequest("abc", null));

        verify(testCaseRepository).appendInput(10L, "abc");
        verify(testCaseRepository, never()).appendExpectedOutput(anyLong(), anyString());
    }

    @Test
    void appendChunk_unknownTestCase_throwsNotFound() {
        when(testCaseRepository.findUploadMetaById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.appendChunk(1L, 99L, new AppendTestCaseChunkRequest("a", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEST_CASE_NOT_FOUND);
    }

    @Test
    void appendChunk_wrongProblem_throwsNotBelong() {
        when(testCaseRepository.findUploadMetaById(10L))
                .thenReturn(Optional.of(new Meta(10L, 2L, true, 0L, 0L)));

        assertThatThrownBy(() -> service.appendChunk(1L, 10L, new AppendTestCaseChunkRequest("a", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEST_CASE_NOT_BELONG_TO_PROBLEM);
    }

    @Test
    void appendChunk_onFinalizedTestCase_throwsNotDraft() {
        when(testCaseRepository.findUploadMetaById(10L))
                .thenReturn(Optional.of(new Meta(10L, 1L, false, 0L, 0L)));

        assertThatThrownBy(() -> service.appendChunk(1L, 10L, new AppendTestCaseChunkRequest("a", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEST_CASE_NOT_DRAFT);

        verify(testCaseRepository, never()).appendInput(anyLong(), anyString());
    }

    @Test
    void finalizeUpload_clearsDraft_andIsIdempotentOnNonDrafts() {
        when(testCaseRepository.findUploadMetaById(10L))
                .thenReturn(Optional.of(new Meta(10L, 1L, false, 7L, 7L)));

        TestCaseUploadStatusResponse res = service.finalizeUpload(1L, 10L);

        verify(testCaseRepository).clearDraft(10L);
        assertThat(res.draft()).isFalse();
        assertThat(res.inputLength()).isEqualTo(7L);
    }
}
