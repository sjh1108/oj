package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.ProblemAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProblemAnnouncementRepository extends JpaRepository<ProblemAnnouncement, Long> {

    /**
     * Distinct study dates already announced in [from, before). Distinct, so
     * re-sending a corrected notice for the same study doesn't bump the week.
     */
    @Query("select count(distinct a.studyDate) from ProblemAnnouncement a "
            + "where a.studyDate >= :from and a.studyDate < :before")
    long countStudyDatesBetween(@Param("from") LocalDate from, @Param("before") LocalDate before);

    List<ProblemAnnouncement> findTop10ByOrderByStudyDateDescIdDesc();
}
