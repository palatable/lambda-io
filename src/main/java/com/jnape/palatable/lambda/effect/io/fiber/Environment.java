package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory;

public record Environment(Timer timer,
                          Executor defaultExecutor,
                          Executor blockingExecutor,
                          Supplier<Canceller> cancellerFactory) {

    public static Environment fromSettings(EnvironmentSettings environmentSettings) {
        boolean       interruptFuturesOnCancel = environmentSettings.interruptFuturesOnCancel();
        AtomicInteger threadCounter            = new AtomicInteger(1);
        int           virtualThreads           = getRuntime().availableProcessors();
        return new Environment(
                Timer.timer(newSingleThreadScheduledExecutor(r -> new Thread(r) {{
                                setDaemon(true);
                                setPriority(MAX_PRIORITY);
                            }}),
                            interruptFuturesOnCancel),
                new ForkJoinPool(
                        virtualThreads,
                        pool -> {
                            ForkJoinWorkerThread thread = defaultForkJoinWorkerThreadFactory.newThread(pool);
                            thread.setName(format("lambda-io-event-loop-[%s/%s]",
                                                  threadCounter.getAndIncrement(),
                                                  virtualThreads));
                            return thread;
                        },
                        null, //todo: Global reporter?
                        true),
                newCachedThreadPool(/* todo: named threads */),
                Canceller::canceller);
    }

    public static Environment system() {
        return System.LOADED;
    }

    private static final class System {
        private static final Environment LOADED = fromSettings(EnvironmentSettings.system());
    }
}