package com.jnape.palatable.lambda.effect.io.fiber.benchmark;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static com.jnape.palatable.lambda.effect.io.fiber.Canceller.canceller;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.forever;
import static com.jnape.palatable.lambda.effect.io.fiber.benchmark.Sample.sample;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

public class FiberBenchmark {

    private static void doSample(Class<?> clazz, Executor executor) {
        Sample sample = sample(format("Fiber (%s)", clazz.getSimpleName()), 10_000_000L, MICROSECONDS);
        executor.execute(() -> forever(fiber(sample::mark)).execute(executor::execute, canceller(), System.out::println));
    }

    /*
    [main] Sample <Fiber (Trampolined)>: 100000000 (35.785114/us average, 2794458us elapsed)
    [main] Sample <Fiber (Trampolined)>: 200000000 (36.506561/us average, 5478467us elapsed)
     */
    public static final class Trampolined {

        public static void main(String[] args) {
            doSample(Trampolined.class, sameThreadScheduler()::schedule);
        }
    }

    /*
    [pool-1-thread-1] Sample <Fiber (Forked_SingleThreaded)>: 100000000 (20.553679/us average, 4865309us elapsed)
    [pool-1-thread-1] Sample <Fiber (Forked_SingleThreaded)>: 200000000 (20.504440/us average, 9753985us elapsed)
     */
    public static final class Forked_SingleThreaded {

        public static void main(String[] args) {
            doSample(Forked_SingleThreaded.class, newSingleThreadExecutor());
        }
    }

    /*
    [pool-1-thread-1] Sample <Fiber (Forked_FixedMultiThreadedPool)>: 100000000 (11.739350/us average, 8518359us elapsed)
    [pool-1-thread-1] Sample <Fiber (Forked_FixedMultiThreadedPool)>: 200000000 (11.620783/us average, 17210545us elapsed)
     */
    public static final class Forked_FixedMultiThreadedPool {

        public static void main(String[] args) {
            doSample(Forked_FixedMultiThreadedPool.class, newFixedThreadPool(3));
        }
    }

    /*
    [pool-1-thread-3] Sample <Fiber (Forked_ElasticPool)>: 1000000 (0.436695/us average, 2289927us elapsed)
    [pool-1-thread-3] Sample <Fiber (Forked_ElasticPool)>: 2000000 (0.437152/us average, 4575065us elapsed)
     */
    public static final class Forked_ElasticPool {

        public static void main(String[] args) {
            doSample(Forked_ElasticPool.class, newCachedThreadPool());
        }
    }

    /*
    [ForkJoinPool.commonPool-worker-7] Sample <Fiber (Forked_ElasticPool)>: 10000000 (6.598478/us average, 1515501us elapsed)
    [ForkJoinPool.commonPool-worker-9] Sample <Fiber (Forked_ElasticPool)>: 20000000 (7.296477/us average, 2741049us elapsed)
     */
    public static final class Forked_ForkJoinPool {

        public static void main(String[] args) throws InterruptedException {
            doSample(Forked_ElasticPool.class, ForkJoinPool.commonPool());
            currentThread().join();
        }
    }
}
