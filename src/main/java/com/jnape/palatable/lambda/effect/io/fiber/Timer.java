package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface Timer {

    Runnable delay(Runnable runnable, long delay, TimeUnit timeUnit);

    static Timer timer(ScheduledExecutorService scheduledExecutorService,
                       boolean interruptFuturesOnCancel) {
        return (runnable, delay, timeUnit) -> {
            ScheduledFuture<?> future = scheduledExecutorService.schedule(runnable, delay, timeUnit);
            return () -> future.cancel(interruptFuturesOnCancel);
        };
    }
}
