package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory;

public record Environment(Scheduler scheduler,
                          Executor defaultExecutor,
                          Executor blockingExecutor,
                          Supplier<Canceller> cancellerFactory,
                          Configuration configuration) {

    public static Environment fromConfiguration(Configuration configuration) {
        Scheduler scheduler = new Scheduler() {
            private final boolean mayInterruptIfRunning = configuration.mayInterruptFuturesOnCancel();
            private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r) {{
                setDaemon(true);
                setPriority(MAX_PRIORITY);
            }});

            @Override
            public Runnable schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
                ScheduledFuture<?> future = ses.schedule(runnable, delay, timeUnit);
                return () -> future.cancel(mayInterruptIfRunning);
            }
        };

        AtomicInteger threadCounter  = new AtomicInteger(1);
        int           virtualThreads = getRuntime().availableProcessors();
        ExecutorService defaultExecutor = new ForkJoinPool(
                virtualThreads,
                pool -> {
                    ForkJoinWorkerThread thread = defaultForkJoinWorkerThreadFactory.newThread(pool);
                    thread.setName("lambda-io-event-loop-[" + threadCounter.getAndIncrement() + "/" + virtualThreads + "]");
                    return thread;
                },
                null, true);
        ExecutorService blockingExecutor = Executors.newCachedThreadPool();
        return new Environment(scheduler, defaultExecutor, blockingExecutor, Canceller::canceller, configuration);
    }

    public static Environment process() {
        return fromConfiguration(Configuration.process());
    }
}