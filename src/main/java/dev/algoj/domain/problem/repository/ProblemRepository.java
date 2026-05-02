package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Page<Problem> findAllByIsPublicTrue(Pageable pageable);
}
