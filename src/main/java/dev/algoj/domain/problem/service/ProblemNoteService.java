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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 문제별 개인 메모. 항상 "로그인한 본인 것"만 다루므로 별도 소유자 검사가 없다 —
 * userId는 인증 주체에서만 들어온다.
 */
@Service
@RequiredArgsConstructor
public class ProblemNoteService {

    private final ProblemNoteRepository noteRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;

    /** 메모가 없으면 빈 응답(content=null)을 돌려준다. */
    @Transactional(readOnly = true)
    public ProblemNoteResponse get(Long userId, Long problemId) {
        requireProblem(problemId);
        return noteRepository.findByUserIdAndProblemId(userId, problemId)
                .map(ProblemNoteResponse::from)
                .orElseGet(ProblemNoteResponse::empty);
    }

    /**
     * 내용이 있으면 upsert, 비었으면 행을 지운다.
     * 지운 뒤에도 200 + content=null 로 응답한다(프론트는 저장과 삭제를 구분하지 않는다).
     */
    @Transactional
    public ProblemNoteResponse update(Long userId, Long problemId, UpdateProblemNoteRequest request) {
        Problem problem = requireProblem(problemId);
        Optional<ProblemNote> existing = noteRepository.findByUserIdAndProblemId(userId, problemId);
        String content = request.content() == null ? "" : request.content().strip();

        if (content.isEmpty()) {
            existing.ifPresent(noteRepository::delete);
            return ProblemNoteResponse.empty();
        }

        ProblemNote note = existing.orElseGet(
                () -> ProblemNote.of(requireUser(userId), problem, content));
        note.updateContent(content);
        return ProblemNoteResponse.from(noteRepository.save(note));
    }

    /**
     * 제출이 정답 처리될 때 남길 메모 본문. 메모가 없으면 null.
     * (스냅샷을 뜨는 쪽은 {@code JudgeService} — 여기서는 읽기만 한다.)
     */
    @Transactional(readOnly = true)
    public String contentForSnapshot(Long userId, Long problemId) {
        return noteRepository.findByUserIdAndProblemId(userId, problemId)
                .map(ProblemNote::getContent)
                .orElse(null);
    }

    private Problem requireProblem(Long problemId) {
        return problemRepository.findById(problemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_NOT_FOUND));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
