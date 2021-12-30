package com.jnape.palatable.lambda.effect.io.fiber;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public interface Scheduler extends Executor {

    final class Shared implements Scheduler {
        private static final Shared INSTANCE = new Shared(
                1,
                new ThreadFactory() {
                    private final AtomicLong counter = new AtomicLong(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "fiber-thread-" + counter.getAndIncrement()){{
                            setDaemon(false);
                        }};
                    }
                });

        private final ScheduledExecutorService ex;

        private Shared(int corePoolSize, ThreadFactory threadFactory) {
            ex = newScheduledThreadPool(corePoolSize, threadFactory);
        }

        public void shutdown() {
            ex.shutdownNow();
        }

        @Override
        public void schedule(Runnable task) {
            ex.execute(task);
        }

        @Override
        public CancelToken delay(Duration delay, Runnable task) {
            ScheduledFuture<?> f = ex.schedule(task, delay.toMillis(), MILLISECONDS);
            return () -> f.cancel(true);
        }
    }

    void schedule(Runnable task);

    CancelToken delay(Duration delay, Runnable task);

    @Override
    default void execute(Runnable command) {
        schedule(command);
    }

    static Scheduler.Shared shared() {
        return Shared.INSTANCE;
    }
}
