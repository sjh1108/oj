package dev.algoj.global.monitor;

import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.submission.repository.SubmissionRepository;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final DataSource dataSource;
    private final AmqpAdmin amqpAdmin;
    private final Judge0Client judge0Client;
    private final SubmissionRepository submissionRepository;

    @Transactional(readOnly = true)
    public MonitorResponse snapshot() {
        return new MonitorResponse(
                isDbUp(),
                judge0Client.isUp(),
                queueStats(),
                submissionStats(),
                jvmStats()
        );
    }

    private boolean isDbUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception e) {
            return false;
        }
    }

    private MonitorResponse.QueueStats queueStats() {
        try {
            Properties judge = amqpAdmin.getQueueProperties(RabbitConfig.JUDGE_QUEUE);
            Properties dlq = amqpAdmin.getQueueProperties(RabbitConfig.JUDGE_DLQ);
            // Reachable broker but missing queue → null Properties.
            return new MonitorResponse.QueueStats(
                    true,
                    intProp(judge, "QUEUE_MESSAGE_COUNT"),
                    intProp(judge, "QUEUE_CONSUMER_COUNT"),
                    intProp(dlq, "QUEUE_MESSAGE_COUNT")
            );
        } catch (Exception e) {
            log.warn("RabbitMQ unreachable for monitor snapshot: {}", e.getMessage());
            return new MonitorResponse.QueueStats(false, null, null, null);
        }
    }

    private Integer intProp(Properties props, String key) {
        if (props == null) return null;
        Object value = props.get(key);
        return value instanceof Number n ? n.intValue() : null;
    }

    private MonitorResponse.SubmissionStats submissionStats() {
        return new MonitorResponse.SubmissionStats(
                submissionRepository.countByStatus(Submission.Status.PENDING),
                submissionRepository.countByStatus(Submission.Status.JUDGING),
                submissionRepository.countByCreatedAtGreaterThanEqual(LocalDate.now().atStartOfDay())
        );
    }

    private MonitorResponse.JvmStats jvmStats() {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return new MonitorResponse.JvmStats(usedMb, maxMb, uptimeSeconds);
    }
}
