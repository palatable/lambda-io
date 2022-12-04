package com.jnape.palatable.lambda.runtime.fiber.benchmark;

import com.jnape.palatable.lambda.runtime.fiber.Fiber;

import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.runtime.fiber.Canceller.canceller;
import static com.jnape.palatable.lambda.runtime.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.runtime.fiber.Fiber.forever;
import static com.jnape.palatable.lambda.runtime.fiber.benchmark.Sample.sample;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static java.lang.String.format;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

public class FiberBenchmark {

    private static void doSample(Class<?> clazz, Executor executor) {
        Sample sample = sample(format("Fiber (%s)", clazz.getSimpleName()), 100_000_000L, MICROSECONDS);
        executor.execute(() -> forever(fiber(sample::mark)).execute(executor::execute, canceller(), System.out::println));
    }

    public static final class Recursive {

        public static void main(String[] args) {
            doSample(Recursive.class, Runnable::run);
        }
    }

    public static final class Trampolined {

        public static void main(String[] args) {
            doSample(Trampolined.class, sameThreadScheduler()::schedule);
        }
    }

    public static final class Forked_SingleThreaded {

        public static void main(String[] args) {
            doSample(Forked_SingleThreaded.class, newSingleThreadExecutor());
        }
    }

    public static final class Forked_FixedMultiThreadedPool {

        public static void main(String[] args) {
            doSample(Forked_FixedMultiThreadedPool.class, newFixedThreadPool(3));
        }
    }

    public static final class Forked_ElasticPool {

        public static void main(String[] args) {
            doSample(Forked_ElasticPool.class, newCachedThreadPool());
        }
    }
}
