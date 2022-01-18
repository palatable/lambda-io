package com.jnape.palatable.lambda.benchmark;

import com.jnape.palatable.lambda.effect.io.fiber.Fiber;
import com.jnape.palatable.lambda.effect.io.fiber2.old.Cancel;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult;
import com.jnape.palatable.lambda.effect.io.fiber2.old.Scheduler;
import com.jnape.palatable.lambda.effect.io.fiber2.scheduler.Trampoline;

import java.util.concurrent.Executors;

import static com.jnape.palatable.lambda.benchmark.Sample.sample;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.forever;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

public class FiberBenchmark {
    private static final Fiber<?> NATIVE_FIBER =
            forever(fiber(sample("native fiber", 100_000_000L, MICROSECONDS)::mark));

    public static final class Trampolined {
        public static void main(String[] args) {
            forever(fiber(sample("native fiber", 100_000_000L, MICROSECONDS)::mark))
                    .execute(Trampoline.trampoline(), Cancel.root(), ex -> ((FiberResult.Failure<?>)ex).ex().printStackTrace());
        }
    }

    public static final class Forked_SingleThreaded {
        public static void main(String[] args) {
            forever(fiber(sample("native fiber", 100_000_000L, MICROSECONDS)::mark))
                    .execute(Scheduler.executorBacked(Executors.newSingleThreadExecutor()), Cancel.root(), __ -> {});
        }
    }

    public static final class Forked_FixedPool {
        public static void main(String[] args) {
            forever(fiber(sample("native fiber", 100_000_000L, MICROSECONDS)::mark))
                    .execute(Scheduler.executorBacked(Executors.newFixedThreadPool(3)), Cancel.root(), __ -> {});
        }
    }

    public static final class Forked_ElasticPool {

        public static void main(String[] args) {
            NATIVE_FIBER.execute(Scheduler.executorBacked(Executors.newCachedThreadPool()), Cancel.root(), __ -> {});
        }
    }
}
