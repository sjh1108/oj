package dev.algoj.domain.submission.service;

import dev.algoj.domain.submission.entity.Submission;
import dev.algoj.domain.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Safety net for lost judge-queue messages: a submission is committed BEFORE
 * its queue message is published (SubmissionController), so a broker hiccup in
 * between leaves a PENDING row nothing will ever pick up. Requeue any PENDING
 * submission old enough that its message should long have been consumed.
 *
 * Requeueing something that is merely waiting in a busy queue just judges it
 * twice back-to-back — harmless, since markJudging() resets the counters on
 * every run and both runs produce the same verdict.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingSubmissionSweeper {

    private final SubmissionRepository submissionRepository;
    private final JudgeQueueProducer judgeQueueProducer;

    @Value("${judge.pending-requeue-after-seconds:300}")
    private long requeueAfterSeconds;

    @Scheduled(fixedDelayString = "${judge.pending-sweep-interval-ms:60000}")
    @Transactional(readOnly = true)
    public void requeueStalePending() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(requeueAfterSeconds);
        List<Submission> stale = submissionRepository
                .findTop50ByStatusAndCreatedAtBeforeOrderByIdAsc(Submission.Status.PENDING, cutoff);
        for (Submission s : stale) {
            log.warn("Requeueing stale PENDING submission {} (created {})", s.getId(), s.getCreatedAt());
            judgeQueueProducer.enqueue(s.getId());
        }
    }
}
