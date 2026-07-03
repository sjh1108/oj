package dev.algoj.domain.submission.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JudgeQueueListenerTest {

    @Mock
    JudgeService judgeService;

    @InjectMocks
    JudgeQueueListener listener;

    @Test
    void onSubmission_delegatesToJudgeService() {
        listener.onSubmission(7L);

        verify(judgeService).judge(7L);
    }
}
