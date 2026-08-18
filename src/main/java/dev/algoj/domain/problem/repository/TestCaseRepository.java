package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

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

    // Same reason as the appends: touching a case's flags must not load its LOBs.
    @Modifying
    @Query("update TestCase t set t.orderIndex = :orderIndex, t.isSample = :isSample where t.id = :id")
    void updateMeta(@Param("id") Long id,
                    @Param("orderIndex") Integer orderIndex,
                    @Param("isSample") Boolean isSample);

    @Query("""
            select t.id as id, t.problem.id as problemId, t.isDraft as isDraft,
                   length(t.input) as inputLength, length(t.expectedOutput) as expectedOutputLength
            from TestCase t where t.id = :id
            """)
    Optional<TestCaseUploadMeta> findUploadMetaById(@Param("id") Long id);

    // Admin list: sizes + the head of each case, so a multi-MB case costs a few KB
    // here instead of its full length. Native because JPQL substring() rejects a
    // @Lob (CLOB) argument; CHAR_LENGTH/SUBSTRING behave the same on MySQL and H2.
    @Query(value = """
            select t.id as id, t.order_index as orderIndex,
                   t.is_sample as isSample, t.is_draft as isDraft,
                   char_length(t.input) as inputLength,
                   char_length(t.expected_output) as expectedOutputLength,
                   substring(t.input, 1, :previewChars) as inputPreview,
                   substring(t.expected_output, 1, :previewChars) as expectedOutputPreview
            from test_cases t
            where t.problem_id = :problemId
            order by t.order_index asc
            """, nativeQuery = true)
    List<TestCaseSummary> findSummariesByProblemId(@Param("problemId") Long problemId,
                                                   @Param("previewChars") int previewChars);
}
