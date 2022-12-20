package com.jnape.palatable.lambda.effect.io.fiber.benchmark;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.effect.io.fiber.Fiber;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import static com.jnape.palatable.lambda.effect.io.fiber.Canceller.canceller;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.forever;
import static com.jnape.palatable.lambda.effect.io.fiber.Trampoline.trampoline;
import static com.jnape.palatable.lambda.effect.io.fiber.benchmark.Sample.sample;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

public class FiberBenchmark {

    private static void samplePolymorphicDispatch(Class<?> clazz, Executor executor) {
        Sample sample = sample(format("Polymorphic (%s)", clazz.getSimpleName()), 10_000_000L, MICROSECONDS);
        executor.execute(() -> forever(fiber(sample::mark)).execute(executor::execute, canceller(), System.out::println));
    }

    private static void sampleConditionalDispatch(Class<?> clazz, Executor executor) {
        Sample sample = sample(format("Conditional (%s)", clazz.getSimpleName()), 100_000_000L, MICROSECONDS);
        trampoline(executor::execute).unsafeRunAsync(forever(fiber(sample::mark)), System.out::println);
    }

    public static final class SameThread {

        /*
        [main] Sample <Polymorphic (SameThread)>: 100000000 (35.531311/us average, 2814419us elapsed)
        [main] Sample <Polymorphic (SameThread)>: 200000000 (35.848347/us average, 5579058us elapsed)
        [main] Sample <Polymorphic (SameThread)>: 300000000 (35.987411/us average, 8336248us elapsed)
         */
        public static final class Polymorphic {
            public static void main(String[] args) {
                samplePolymorphicDispatch(SameThread.class, sameThreadScheduler()::schedule);
            }
        }

        /*
        [main] Sample <Conditional (SameThread)>: 100000000 (130.664124/us average, 765321us elapsed)
        [main] Sample <Conditional (SameThread)>: 200000000 (136.192932/us average, 1468505us elapsed)
        [main] Sample <Conditional (SameThread)>: 300000000 (138.391571/us average, 2167762us elapsed)
         */
        public static final class Conditional {
            public static void main(String[] args) {
                sampleConditionalDispatch(SameThread.class, sameThreadScheduler()::schedule);
            }
        }
    }

    public static final class Forked_SingleThreaded {

        /*
        [pool-1-thread-1] Sample <Polymorphic (Forked_SingleThreaded)>: 100000000 (20.395947/us average, 4902935us elapsed)
        [pool-1-thread-1] Sample <Polymorphic (Forked_SingleThreaded)>: 200000000 (20.273880/us average, 9864910us elapsed)
        [pool-1-thread-1] Sample <Polymorphic (Forked_SingleThreaded)>: 300000000 (20.175434/us average, 14869568us elapsed)
         */
        public static final class Polymorphic {
            public static void main(String[] args) {
                samplePolymorphicDispatch(Forked_SingleThreaded.class, newSingleThreadExecutor());
            }
        }

        /*
        [pool-1-thread-1] Sample <Conditional (Forked_SingleThreaded)>: 100000000 (136.513367/us average, 732529us elapsed)
        [pool-1-thread-1] Sample <Conditional (Forked_SingleThreaded)>: 200000000 (145.260895/us average, 1376833us elapsed)
        [pool-1-thread-1] Sample <Conditional (Forked_SingleThreaded)>: 300000000 (149.430145/us average, 2007627us elapsed)
         */
        public static final class Conditional {
            public static void main(String[] args) {
                sampleConditionalDispatch(Forked_SingleThreaded.class, newSingleThreadExecutor());
            }
        }
    }

    public static final class Forked_FixedMultiThreadedPool {

        /*
        [pool-1-thread-2] Sample <Polymorphic (Forked_FixedMultiThreadedPool)>: 100000000 (11.477396/us average, 8712778us elapsed)
        [pool-1-thread-1] Sample <Polymorphic (Forked_FixedMultiThreadedPool)>: 200000000 (11.643932/us average, 17176328us elapsed)
        [pool-1-thread-1] Sample <Polymorphic (Forked_FixedMultiThreadedPool)>: 300000000 (11.663974/us average, 25720222us elapsed)
         */
        public static final class Polymorphic {
            public static void main(String[] args) {
                samplePolymorphicDispatch(Forked_FixedMultiThreadedPool.class, newFixedThreadPool(3));
            }
        }

        /*
        [pool-1-thread-3] Sample <Conditional (Forked_FixedMultiThreadedPool)>: 100000000 (114.118774/us average, 876280us elapsed)
        [pool-1-thread-1] Sample <Conditional (Forked_FixedMultiThreadedPool)>: 200000000 (121.425606/us average, 1647099us elapsed)
        [pool-1-thread-2] Sample <Conditional (Forked_FixedMultiThreadedPool)>: 300000000 (125.488777/us average, 2390652us elapsed)
         */
        public static final class Conditional {
            public static void main(String[] args) {
                sampleConditionalDispatch(Forked_FixedMultiThreadedPool.class, newFixedThreadPool(3));
            }
        }
    }

    public static final class Forked_ElasticPool {

        /*
        [pool-1-thread-2] Sample <Polymorphic (Forked_ElasticPool)>: 10000000 (0.474948/us average, 21054943us elapsed)
        [pool-1-thread-2] Sample <Polymorphic (Forked_ElasticPool)>: 20000000 (0.467776/us average, 42755480us elapsed)
        [pool-1-thread-1] Sample <Polymorphic (Forked_ElasticPool)>: 30000000 (0.462199/us average, 64907059us elapsed)
         */
        public static final class Polymorphic {
            public static void main(String[] args) {
                samplePolymorphicDispatch(Forked_ElasticPool.class, newCachedThreadPool());
            }
        }

        /*
        [pool-1-thread-2] Sample <Conditional (Forked_ElasticPool)>: 100000000 (76.163376/us average, 1312967us elapsed)
        [pool-1-thread-1] Sample <Conditional (Forked_ElasticPool)>: 200000000 (81.710625/us average, 2447662us elapsed)
        [pool-1-thread-3] Sample <Conditional (Forked_ElasticPool)>: 300000000 (83.900452/us average, 3575666us elapsed)
         */
        public static final class Conditional {
            public static void main(String[] args) {
                sampleConditionalDispatch(Forked_ElasticPool.class, newCachedThreadPool());
            }
        }
    }

    public static final class Forked_ForkJoinPool {

        /*
        [ForkJoinPool.commonPool-worker-6] Sample <Polymorphic (Forked_ElasticPool)>: 10000000 (5.603858/us average, 1784485us elapsed)
        [ForkJoinPool.commonPool-worker-1] Sample <Polymorphic (Forked_ElasticPool)>: 20000000 (6.473552/us average, 3089494us elapsed)
        [ForkJoinPool.commonPool-worker-3] Sample <Polymorphic (Forked_ElasticPool)>: 30000000 (6.855833/us average, 4375836us elapsed)
         */
        public static final class Polymorphic {
            public static void main(String[] args) throws InterruptedException {
                samplePolymorphicDispatch(Forked_ElasticPool.class, ForkJoinPool.commonPool());
                currentThread().join();
            }
        }

        /*
        [ForkJoinPool.commonPool-worker-7] Sample <Conditional (Forked_ElasticPool)>: 100000000 (88.965118/us average, 1124036us elapsed)
        [ForkJoinPool.commonPool-worker-8] Sample <Conditional (Forked_ElasticPool)>: 200000000 (89.594254/us average, 2232286us elapsed)
        [ForkJoinPool.commonPool-worker-3] Sample <Conditional (Forked_ElasticPool)>: 300000000 (89.057030/us average, 3368628us elapsed)
         */
        public static final class Conditional {
            public static void main(String[] args) throws InterruptedException {
                sampleConditionalDispatch(Forked_ElasticPool.class, ForkJoinPool.commonPool());
                currentThread().join();
            }
        }
    }

    public static final class Experiments {

        public static final class DeepBind {
            public static void main(String[] args) {
                Sample sample = sample(format("Fiber (%s)", Experiments.class.getSimpleName()), 10_000_000L, MICROSECONDS);

                Fiber<Unit> fiber   = fiber(sample::mark);
                Fiber<Unit> forever = times(100_000, f -> f.bind(__ -> f), fiber);

                trampoline(sameThreadScheduler()).unsafeRunAsync(forever, System.out::println);
            }
        }

        public static final class ShallowBind {
            private static <A> Fiber<A> foreverBind(Fiber<?> fiber) {
                return fiber.bind(__ -> foreverBind(fiber));
            }

            public static void main(String[] args) throws InterruptedException {
                Sample sample = sample(format("Fiber (%s)", Experiments.class.getSimpleName()), 10_000_000L, MICROSECONDS);

                Fiber<Unit> fiber   = fiber(sample::mark);
                Fiber<Unit> forever = foreverBind(times(100_000, f -> f.bind(__ -> f), fiber));

                trampoline(sameThreadScheduler()).unsafeRunAsync(forever, System.out::println);
                Thread.currentThread().join();
            }
        }
    }
}
