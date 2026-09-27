package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.ProblemAnnouncement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:algoj;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        // Hibernate builds the H2 schema here; the Flyway baseline is MySQL-only DDL.
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "algoj.seed-admin=false",
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProblemAnnouncementRepositoryTest {

    @Autowired
    ProblemAnnouncementRepository repository;

    private void announce(String date) {
        repository.save(ProblemAnnouncement.of(LocalDate.parse(date), 1, "x"));
    }

    private long weekBefore(String date) {
        LocalDate d = LocalDate.parse(date);
        return repository.countStudyDatesBetween(d.withDayOfMonth(1), d) + 1;
    }

    @Test
    void skippedWeeksDoNotCount_onlyAnnouncedStudiesDo() {
        // September studies, then Oct 5 skipped: Oct 12 is October's first study.
        announce("2026-09-14");
        announce("2026-09-21");

        assertThat(weekBefore("2026-10-12")).isEqualTo(1);

        announce("2026-10-12");
        assertThat(weekBefore("2026-10-19")).isEqualTo(2);
    }

    @Test
    void resendingForTheSameStudy_keepsItsWeek_andCountsOnceForLaterOnes() {
        announce("2026-10-12");
        announce("2026-10-12"); // corrected notice for the same study

        assertThat(weekBefore("2026-10-12")).isEqualTo(1);
        assertThat(weekBefore("2026-10-19")).isEqualTo(2);
    }

    @Test
    void recent_isNewestStudyFirst() {
        announce("2026-09-14");
        announce("2026-10-12");
        announce("2026-09-21");

        assertThat(repository.findTop10ByOrderByStudyDateDescIdDesc())
                .extracting(ProblemAnnouncement::getStudyDate)
                .containsExactly(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-09-21"),
                        LocalDate.parse("2026-09-14"));
    }
}
