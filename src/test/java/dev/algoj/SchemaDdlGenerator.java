package dev.algoj;

import dev.algoj.domain.problem.entity.Problem;
import dev.algoj.domain.problem.entity.Subtask;
import dev.algoj.domain.problem.entity.TestCase;
import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.user.entity.User;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Dumps the MySQL DDL Hibernate would emit for the current entities to
 * build/baseline-ddl.sql. Not a test of behavior — it's the tool used to write
 * db/migration/V1__baseline.sql, kept around so future entity changes can be
 * diffed against it when authoring the next Flyway migration.
 */
class SchemaDdlGenerator {

    @Test
    void dump() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                // Match Spring Boot's defaults so column names come out snake_case.
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy")
                .build();
        Metadata metadata = new MetadataSources(registry)
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(Problem.class)
                .addAnnotatedClass(TestCase.class)
                .addAnnotatedClass(Subtask.class)
                .addAnnotatedClass(Submission.class)
                .buildMetadata();

        Map<String, Object> settings = new HashMap<>();
        settings.put("jakarta.persistence.schema-generation.database.action", "none");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", "build/baseline-ddl.sql");
        settings.put("hibernate.hbm2ddl.delimiter", ";");

        SchemaManagementToolCoordinator.process(metadata, registry, settings, null);
    }
}
