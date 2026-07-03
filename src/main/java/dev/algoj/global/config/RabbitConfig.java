package dev.algoj.global.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Judge queue topology.
 *
 * Submissions are enqueued as durable messages, so PENDING work survives a
 * broker/app restart, and a worker crash mid-judge redelivers the unacked
 * message (no more orphaned JUDGING rows). Messages the listener rejects
 * (e.g. undeserializable payloads) are NOT requeued — they land in the DLQ
 * for inspection instead of looping forever.
 */
@Configuration
public class RabbitConfig {

    public static final String JUDGE_EXCHANGE = "judge.exchange";
    public static final String JUDGE_QUEUE = "judge.queue";
    public static final String JUDGE_ROUTING_KEY = "judge.submission";
    public static final String JUDGE_DLX = "judge.dlx";
    public static final String JUDGE_DLQ = "judge.queue.dlq";

    @Bean
    public DirectExchange judgeExchange() {
        return new DirectExchange(JUDGE_EXCHANGE);
    }

    @Bean
    public Queue judgeQueue() {
        return QueueBuilder.durable(JUDGE_QUEUE)
                .deadLetterExchange(JUDGE_DLX)
                .deadLetterRoutingKey(JUDGE_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding judgeBinding() {
        return BindingBuilder.bind(judgeQueue()).to(judgeExchange()).with(JUDGE_ROUTING_KEY);
    }

    @Bean
    public DirectExchange judgeDeadLetterExchange() {
        return new DirectExchange(JUDGE_DLX);
    }

    @Bean
    public Queue judgeDeadLetterQueue() {
        return QueueBuilder.durable(JUDGE_DLQ).build();
    }

    @Bean
    public Binding judgeDeadLetterBinding() {
        return BindingBuilder.bind(judgeDeadLetterQueue()).to(judgeDeadLetterExchange()).with(JUDGE_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
