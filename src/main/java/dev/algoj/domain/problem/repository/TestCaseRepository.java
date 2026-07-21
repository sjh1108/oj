package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderByOrderIndexAsc(Long problemId);

    // Problem-detail shows only sample cases. Loading the full collection would
    // drag every hidden case's LONGTEXT input/output over the wire (slow on RDS).
    List<TestCase> findByProblemIdAndIsSampleTrueAndIsDraftFalseOrderByOrderIndexAsc(Long problemId);

    // Chunked upload appends run as in-place CONCAT updates: loading the entity
    // would drag the full LOBs through the persistence context on every chunk
    // (O(total²) I/O for a multi-MB case).
    @Modifying
    @Query("update TestCase t set t.input = concat(t.input, :chunk) where t.id = :id")
    void appendInput(@Param("id") Long id, @Param("chunk") String chunk);

    @Modifying
    @Query("update TestCase t set t.expectedOutput = concat(t.expectedOutput, :chunk) where t.id = :id")
    void appendExpectedOutput(@Param("id") Long id, @Param("chunk") String chunk);

    @Modifying
    @Query("update TestCase t set t.isDraft = false where t.id = :id")
    void clearDraft(@Param("id") Long id);

    @Query("""
            select t.id as id, t.problem.id as problemId, t.isDraft as isDraft,
                   length(t.input) as inputLength, length(t.expectedOutput) as expectedOutputLength
            from TestCase t where t.id = :id
            """)
    Optional<TestCaseUploadMeta> findUploadMetaById(@Param("id") Long id);
}
