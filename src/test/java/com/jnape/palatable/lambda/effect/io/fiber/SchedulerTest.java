package com.jnape.palatable.lambda.effect.io.fiber;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.jnape.palatable.lambda.effect.io.fiber.Scheduler.scheduler;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class SchedulerTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    public class FromScheduledExecutorService {
        @Mock ScheduledExecutorService scheduledExecutorService;

        @Test
        public void delegatesToBackingExecutor() {
            Runnable runnable = () -> {};
            scheduler(scheduledExecutorService).schedule(runnable, 1, SECONDS);

            verify(scheduledExecutorService).schedule(runnable, 1, SECONDS);
            verifyNoMoreInteractions(scheduledExecutorService);
        }

        @Test
        public void cancelPropagates() {
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            when(scheduledExecutorService.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocationOnMock -> future);
            Scheduler scheduler = scheduler(scheduledExecutorService);
            Runnable  cancel    = scheduler.schedule(() -> {}, 1, SECONDS);

            verifyNoInteractions(future);
            cancel.run();
            verify(future).cancel(false);
            verifyNoMoreInteractions(future);
        }

    }
}