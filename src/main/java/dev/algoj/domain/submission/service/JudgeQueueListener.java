package dev.algoj.domain.submission.service;

import dev.algoj.global.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JudgeQueueListener {

    private final JudgeService judgeService;

    @RabbitListener(queues = RabbitConfig.JUDGE_QUEUE)
    public void onSubmission(Long submissionId) {
        judgeService.judge(submissionId);
    }
}
