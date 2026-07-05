package dev.algoj.global.monitor;

import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.submission.repository.SubmissionRepository;
import dev.algoj.global.client.Judge0Client;
import dev.algoj.global.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    DataSource dataSource;
    @Mock
    AmqpAdmin amqpAdmin;
    @Mock
    Judge0Client judge0Client;
    @Mock
    SubmissionRepository submissionRepository;

    @InjectMocks
    MonitorService service;

    @Test
    void snapshot_mapsQueueAndSubmissionStats() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(judge0Client.isUp()).thenReturn(true);

        Properties judgeProps = new Properties();
        judgeProps.put("QUEUE_MESSAGE_COUNT", 3);
        judgeProps.put("QUEUE_CONSUMER_COUNT", 2);
        Properties dlqProps = new Properties();
        dlqProps.put("QUEUE_MESSAGE_COUNT", 0);
        when(amqpAdmin.getQueueProperties(RabbitConfig.JUDGE_QUEUE)).thenReturn(judgeProps);
        when(amqpAdmin.getQueueProperties(RabbitConfig.JUDGE_DLQ)).thenReturn(dlqProps);

        when(submissionRepository.countByStatus(Submission.Status.PENDING)).thenReturn(3L);
        when(submissionRepository.countByStatus(Submission.Status.JUDGING)).thenReturn(1L);
        when(submissionRepository.countByCreatedAtGreaterThanEqual(any(LocalDateTime.class)))
                .thenReturn(12L);

        MonitorResponse res = service.snapshot();

        assertThat(res.dbUp()).isTrue();
        assertThat(res.judge0Up()).isTrue();
        assertThat(res.judgeQueue())
                .isEqualTo(new MonitorResponse.QueueStats(true, 3, 2, 0));
        assertThat(res.submissions())
                .isEqualTo(new MonitorResponse.SubmissionStats(3, 1, 12));
        assertThat(res.jvm().heapMaxMb()).isPositive();
    }

    @Test
    void snapshot_reportsBrokerDownInsteadOfThrowing() throws Exception {
        when(dataSource.getConnection()).thenThrow(new RuntimeException("db down"));
        when(judge0Client.isUp()).thenReturn(false);
        when(amqpAdmin.getQueueProperties(RabbitConfig.JUDGE_QUEUE))
                .thenThrow(new AmqpConnectException(new RuntimeException("refused")));

        MonitorResponse res = service.snapshot();

        assertThat(res.dbUp()).isFalse();
        assertThat(res.judge0Up()).isFalse();
        assertThat(res.judgeQueue())
                .isEqualTo(new MonitorResponse.QueueStats(false, null, null, null));
    }

    @Test
    void snapshot_missingQueueYieldsNullCountsButBrokerUp() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(judge0Client.isUp()).thenReturn(true);
        when(amqpAdmin.getQueueProperties(any())).thenReturn(null);

        MonitorResponse res = service.snapshot();

        assertThat(res.judgeQueue())
                .isEqualTo(new MonitorResponse.QueueStats(true, null, null, null));
    }
}
