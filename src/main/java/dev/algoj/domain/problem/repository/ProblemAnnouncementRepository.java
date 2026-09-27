package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.ProblemAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemAnnouncementRepository extends JpaRepository<ProblemAnnouncement, Long> {

    /**
     * Highest week already announced in the month, 0 if none. Max rather than a
     * count, so re-sending a corrected notice for the same week doesn't bump it.
     */
    @Query("select coalesce(max(a.weekOfMonth), 0) from ProblemAnnouncement a "
            + "where a.year = :year and a.month = :month")
    int findMaxWeek(@Param("year") int year, @Param("month") int month);

    List<ProblemAnnouncement> findTop10ByOrderByYearDescMonthDescWeekOfMonthDescIdDesc();
}
