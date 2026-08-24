package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.ProblemNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProblemNoteRepository extends JpaRepository<ProblemNote, Long> {

    Optional<ProblemNote> findByUserIdAndProblemId(Long userId, Long problemId);
}
