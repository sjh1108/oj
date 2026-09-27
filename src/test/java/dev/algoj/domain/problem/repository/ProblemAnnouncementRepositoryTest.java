package dev.algoj.domain.problem.repository;

import dev.algoj.domain.problem.entity.ProblemAnnouncement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

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

    private void announce(int year, int month, int week) {
        repository.save(ProblemAnnouncement.of(year, month, week, "x"));
    }

    @Test
    void maxWeek_isPerMonth_andZeroForAMonthWithNothingSent() {
        announce(2026, 9, 2);
        announce(2026, 9, 3);

        assertThat(repository.findMaxWeek(2026, 9)).isEqualTo(3);
        assertThat(repository.findMaxWeek(2026, 10)).isZero();
        // Same month a year later is a different month.
        assertThat(repository.findMaxWeek(2027, 9)).isZero();
    }

    @Test
    void resendingTheSameWeek_doesNotBumpTheMax() {
        announce(2026, 10, 1);
        announce(2026, 10, 1); // corrected notice for the same study

        assertThat(repository.findMaxWeek(2026, 10)).isEqualTo(1);
    }

    @Test
    void recent_isNewestWeekFirst() {
        announce(2026, 9, 3);
        announce(2026, 10, 1);
        announce(2026, 9, 4);

        assertThat(repository.findTop10ByOrderByYearDescMonthDescWeekOfMonthDescIdDesc())
                .extracting(a -> a.getMonth() + "/" + a.getWeekOfMonth())
                .containsExactly("10/1", "9/4", "9/3");
    }
}
