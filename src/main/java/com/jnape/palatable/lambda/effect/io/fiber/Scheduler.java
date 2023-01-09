package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface Scheduler {

    Runnable schedule(Runnable runnable, long delay, TimeUnit timeUnit);

    static Scheduler scheduler(ScheduledExecutorService scheduledExecutorService) {
        return (runnable, delay, timeUnit) -> {
            ScheduledFuture<?> future = scheduledExecutorService.schedule(runnable, delay, timeUnit);
            //todo: interrupt or not?
            return () -> future.cancel(false);
        };
    }
}
