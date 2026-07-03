package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long>, JpaSpecificationExecutor<Problem> {

    /** Every distinct tag in use, for the filter dropdown. */
    @Query("select distinct t from Problem p join p.tags t " +
            "where :includePrivate = true or p.isPublic = true " +
            "order by t")
    List<String> findAllTags(@Param("includePrivate") boolean includePrivate);
}
