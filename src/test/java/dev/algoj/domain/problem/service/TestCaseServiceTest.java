package dev.algoj.domain.problem.service;

import dev.algoj.domain.problem.dto.AppendTestCaseChunkRequest;
import dev.algoj.domain.problem.dto.TestCaseMetaRequest;
import dev.algoj.domain.problem.dto.TestCaseRequest;
import dev.algoj.domain.problem.dto.TestCaseResponse;
import dev.algoj.domain.problem.dto.TestCaseSummaryResponse;
import dev.algoj.domain.problem.dto.TestCaseUploadStatusResponse;
import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.problem.repository.ProblemRepository;
import dev.algoj.domain.problem.repository.TestCaseRepository;
import dev.algoj.domain.problem.repository.TestCaseSummary;
import dev.algoj.domain.problem.repository.TestCaseUploadMeta;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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

    private record Summary(Long id, Integer orderIndex, Boolean isSample, Boolean isDraft,
                           Long inputLength, Long expectedOutputLength,
                           String inputPreview, String expectedOutputPreview) implements TestCaseSummary {
        @Override public Long getId() { return id; }
        @Override public Integer getOrderIndex() { return orderIndex; }
        @Override public Boolean getIsSample() { return isSample; }
        @Override public Boolean getIsDraft() { return isDraft; }
        @Override public Long getInputLength() { return inputLength; }
        @Override public Long getExpectedOutputLength() { return expectedOutputLength; }
        @Override public String getInputPreview() { return inputPreview; }
        @Override public String getExpectedOutputPreview() { return expectedOutputPreview; }
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

    @Test
    void listSummaries_returnsSizesAndPreviews_withoutFullData() {
        when(problemRepository.existsById(1L)).thenReturn(true);
        when(testCaseRepository.findSummariesByProblemId(1L, TestCaseService.PREVIEW_CHARS))
                .thenReturn(List.of(new Summary(10L, 0, true, false, 3L, 4L, "abc", "wxyz")));

        List<TestCaseSummaryResponse> res = service.listSummaries(1L);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).inputLength()).isEqualTo(3L);
        assertThat(res.get(0).expectedOutputLength()).isEqualTo(4L);
        assertThat(res.get(0).inputPreview()).isEqualTo("abc");
    }

    @Test
    void listSummaries_unknownProblem_throwsNotFound() {
        when(problemRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.listSummaries(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND);
    }

    @Test
    void get_wrongProblem_throwsNotBelong() {
        Problem other = sampleProblem();
        ReflectionTestUtils.setField(other, "id", 2L);
        TestCase tc = TestCase.builder()
                .problem(other).input("in").expectedOutput("out")
                .orderIndex(0).isSample(false).build();
        when(testCaseRepository.findById(10L)).thenReturn(Optional.of(tc));

        assertThatThrownBy(() -> service.get(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEST_CASE_NOT_BELONG_TO_PROBLEM);
    }

    @Test
    void updateMeta_updatesFlagsInPlace_withoutLoadingTheCase() {
        when(testCaseRepository.findUploadMetaById(10L))
                .thenReturn(Optional.of(new Meta(10L, 1L, false, 900000L, 900000L)));

        service.updateMeta(1L, 10L, new TestCaseMetaRequest(3, true));

        verify(testCaseRepository).updateMeta(10L, 3, true);
        verify(testCaseRepository, never()).findById(anyLong());
    }

    @Test
    void updateMeta_wrongProblem_throwsNotBelong() {
        when(testCaseRepository.findUploadMetaById(10L))
                .thenReturn(Optional.of(new Meta(10L, 2L, false, 1L, 1L)));

        assertThatThrownBy(() -> service.updateMeta(1L, 10L, new TestCaseMetaRequest(0, false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEST_CASE_NOT_BELONG_TO_PROBLEM);

        verify(testCaseRepository, never()).updateMeta(anyLong(), any(), any());
    }
}
