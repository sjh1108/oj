package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderByOrderIndexAsc(Long problemId);
}
