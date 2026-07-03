package dev.algoj.domain.submission.service;

import dev.algoj.global.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JudgeQueueProducer {

    private final RabbitTemplate rabbitTemplate;

    public void enqueue(Long submissionId) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.JUDGE_EXCHANGE,
                RabbitConfig.JUDGE_ROUTING_KEY,
                submissionId);
    }
}
