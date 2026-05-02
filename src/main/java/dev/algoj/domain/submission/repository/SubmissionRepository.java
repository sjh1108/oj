package dev.algoj.domain.submission.repository;

import dev.algoj.domain.submission.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Page<Submission> findAllByUserId(Long userId, Pageable pageable);
}
