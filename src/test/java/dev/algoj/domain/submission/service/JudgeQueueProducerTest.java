package dev.algoj.domain.submission.service;

import dev.algoj.global.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JudgeQueueProducerTest {

    @Mock
    RabbitTemplate rabbitTemplate;

    @InjectMocks
    JudgeQueueProducer producer;

    @Test
    void enqueue_publishesSubmissionIdToJudgeExchange() {
        producer.enqueue(42L);

        verify(rabbitTemplate).convertAndSend(
                RabbitConfig.JUDGE_EXCHANGE,
                RabbitConfig.JUDGE_ROUTING_KEY,
                (Object) 42L);
    }
}
