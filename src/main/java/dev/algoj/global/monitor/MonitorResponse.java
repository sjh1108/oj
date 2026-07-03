package dev.algoj.global.monitor;

/** Snapshot served to the Discord bot's /서버상태 command. */
public record MonitorResponse(
        boolean dbUp,
        boolean judge0Up,
        QueueStats judgeQueue,
        SubmissionStats submissions,
        JvmStats jvm
) {
    /** null counts mean the broker was reachable but the queue is missing. */
    public record QueueStats(
            boolean brokerUp,
            Integer messages,
            Integer consumers,
            Integer deadLettered
    ) {}

    public record SubmissionStats(
            long pending,
            long judging,
            long submittedToday
    ) {}

    public record JvmStats(
            long heapUsedMb,
            long heapMaxMb,
            long uptimeSeconds
    ) {}
}
