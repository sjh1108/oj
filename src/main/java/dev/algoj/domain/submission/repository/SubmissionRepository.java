package dev.algoj.domain.submission.repository;

import dev.algoj.domain.submission.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Page<Submission> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndProblemIdAndStatus(Long userId, Long problemId, Submission.Status status);

    Page<Submission> findAllByProblemIdAndStatusAndIsPublicTrue(
            Long problemId,
            Submission.Status status,
            Pageable pageable
    );

    @Query("select distinct s.problem.id from Submission s " +
            "where s.user.id = :userId and s.status = dev.algoj.domain.submission.entity.Submission.Status.ACCEPTED")
    List<Long> findDistinctSolvedProblemIdsByUserId(@Param("userId") Long userId);
}
