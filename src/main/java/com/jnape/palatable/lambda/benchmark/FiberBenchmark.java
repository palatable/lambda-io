package com.jnape.palatable.lambda.benchmark;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.effect.io.fiber2.old.Cancel;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult;
import com.jnape.palatable.lambda.effect.io.fiber2.scheduler.Trampoline;
import com.jnape.palatable.lambda.runtime.fiber.Canceller;
import com.jnape.palatable.lambda.runtime.fiber.Fiber;
import com.jnape.palatable.lambda.runtime.fiber.Result;

import java.util.concurrent.Executors;

import static com.jnape.palatable.lambda.benchmark.Sample.sample;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.forever;
import static com.jnape.palatable.lambda.effect.io.fiber2.old.Scheduler.executorBacked;
import static com.jnape.palatable.lambda.runtime.fiber.Scheduler.scheduledExecutorService;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

public class FiberBenchmark {
    private static final Sample SAMPLE = sample("native fiber", 100_000_000L, MICROSECONDS);

    private static final Fiber<?> CLEAN_FIBER =
            Fiber.forever((s, c, k) -> {
                SAMPLE.mark();
                k.accept(Result.successful(Unit.UNIT));
            });

    public static final class Trampolined {
        public static void main(String[] args) {
            forever(fiber(sample("native fiber", 100_000_000L, MICROSECONDS)::mark))
                    .execute(Trampoline.trampoline(), Cancel.root(), ex -> ((FiberResult.Failure<?>) ex).ex().printStackTrace());
        }
    }

    public static final class Forked_SingleThreaded {
        public static void main(String[] args) {
            forever(fiber(sample("native fiber", 100_000_000L, MICROSECONDS)::mark))
                    .execute(executorBacked(Executors.newSingleThreadExecutor()), Cancel.root(), __ -> {});
        }
    }

    public static final class Forked_FixedPool {
        public static void main(String[] args) {
            forever(fiber(sample("native fiber", 100_000_000L, MICROSECONDS)::mark))
                    .execute(executorBacked(Executors.newFixedThreadPool(3)), Cancel.root(), __ -> {});
        }
    }

    public static final class Forked_ElasticPool {

        public static void main(String[] args) {
            forever(fiber(SAMPLE::mark)).execute(executorBacked(Executors.newSingleThreadExecutor()), Cancel.root(), __ -> {});
        }
    }

    public static final class New {
        public static void main(String[] args) {
            CLEAN_FIBER.execute(scheduledExecutorService(Executors.newSingleThreadScheduledExecutor()),
                                Canceller.root(),
                                System.out::println);
        }
    }
}
