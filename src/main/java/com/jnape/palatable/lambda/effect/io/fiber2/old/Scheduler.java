package com.jnape.palatable.lambda.effect.io.fiber2.old;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public interface Scheduler {

    final class Shared implements Scheduler {
        private static final Shared INSTANCE = new Shared(
                1,
                new ThreadFactory() {
                    private final AtomicLong counter = new AtomicLong(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "fiber-thread-" + counter.getAndIncrement()) {{
                            setDaemon(true);
                        }};
                    }
                });

        private final ScheduledExecutorService ex;

        private Shared(int corePoolSize, ThreadFactory threadFactory) {
            ex = newScheduledThreadPool(corePoolSize, threadFactory);
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

    static Scheduler.Shared shared() {
        return Shared.INSTANCE;
    }

    static Scheduler executorBacked(Executor executor) {
        if (executor instanceof ScheduledExecutorService ses) {
            return new Scheduler() {
                @Override
                public void schedule(Runnable task) {
                    ses.execute(task);
                }

                @Override
                public CancelToken delay(Duration delay, Runnable task) {
                    ScheduledFuture<?> scheduled = ses.schedule(task, delay.toNanos(), NANOSECONDS);
                    return () -> scheduled.cancel(false);
                }
            };
        }

        return new Scheduler() {
            @Override
            public void schedule(Runnable task) {
                executor.execute(task);
            }

            @Override
            public CancelToken delay(Duration delay, Runnable task) {
                long scheduled = System.nanoTime() + delay.toNanos();
                if (delay.isZero()) {
                    schedule(task);
                    return () -> {};
                }
                AtomicBoolean cancelled = new AtomicBoolean();
                schedule(new Runnable() {
                    @Override
                    public void run() {
                        if (!cancelled.get()) {
                            if (System.nanoTime() > scheduled)
                                task.run();
                            else
                                schedule(this);
                        }
                    }
                });
                return () -> cancelled.set(true);
            }
        };
    }
}
