package com.jnape.palatable.lambda.runtime.fiber;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public interface Scheduler {

    void schedule(Runnable runnable);

    void schedule(Duration delay, Runnable runnable, Canceller canceller);

    static Scheduler scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        return new Scheduler() {
            @Override
            public void schedule(Runnable runnable) {
                scheduledExecutorService.execute(runnable);
            }

            @Override
            public void schedule(Duration delay, Runnable runnable, Canceller canceller) {
                //todo: unsafe
                ScheduledFuture<?> future = scheduledExecutorService
                        .schedule(runnable, delay.toNanos(), NANOSECONDS);
                canceller.onCancel(() -> future.cancel(true));
            }
        };
    }

    static Scheduler elastic() {
        return scheduledExecutorService(new ScheduledThreadPoolExecutor(1));
    }
}
