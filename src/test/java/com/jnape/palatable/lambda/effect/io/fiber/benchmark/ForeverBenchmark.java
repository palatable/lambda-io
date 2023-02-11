package com.jnape.palatable.lambda.effect.io.fiber.benchmark;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.effect.io.fiber.Canceller;
import com.jnape.palatable.lambda.effect.io.fiber.Environment;
import com.jnape.palatable.lambda.effect.io.fiber.Fiber;
import com.jnape.palatable.lambda.effect.io.fiber.Result;
import com.jnape.palatable.lambda.effect.io.fiber.Runtime;
import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.RunnerException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.forever;
import static com.jnape.palatable.lambda.effect.io.fiber.FiberRunLoop.fiberRunLoop;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static com.jnape.palatable.lambda.effect.io.fiber.RuntimeSettings.DEFAULT;
import static com.jnape.palatable.lambda.effect.io.fiber.Scheduler.scheduler;
import static com.jnape.palatable.lambda.effect.io.fiber.benchmark.Benchmark.OPS_PER_BENCHMARK;
import static com.jnape.palatable.lambda.effect.io.fiber.benchmark.Benchmark.runBenchmarks;
import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.Executors.newWorkStealingPool;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.openjdk.jmh.annotations.Level.Invocation;
import static org.openjdk.jmh.annotations.Mode.Throughput;

public class ForeverBenchmark {

    public static void main(String[] args) throws RunnerException {
        runBenchmarks(ForeverBenchmark.class);
    }

    static abstract class RuntimeState {

        private long                            counter;
        private Runtime                         runtime;
        private ExecutorService                 executorService;
        private ScheduledExecutorService        scheduledExecutorService;
        private CompletableFuture<Result<Long>> future;

        abstract ExecutorService newExecutorService();

        @Setup(Invocation)
        public void setup() {
            future                   = new CompletableFuture<>();
            counter                  = 0;
            executorService          = newExecutorService();
            scheduledExecutorService = newSingleThreadScheduledExecutor();
            Scheduler scheduler = scheduler(scheduledExecutorService);
            executorService.execute(() -> {});
            scheduler.schedule(() -> {}, 1, MILLISECONDS);
            runtime = fiberRunLoop(new Environment(scheduler, executorService, executorService, Canceller::canceller), DEFAULT);
        }

        @TearDown(Invocation)
        public void tearDown() {
            executorService.shutdownNow();
            scheduledExecutorService.shutdownNow();
        }

        public Result<Long> run(Fiber<Long> fiber) {
            runtime.unsafeRunAsync(fiber, future::complete);
            return future.join();
        }

        public Result<Unit> cancelAfter(int maxValue) {
            return counter++ >= maxValue ? cancellation() : success();
        }
    }

    @BenchmarkMode(Throughput)
    @OutputTimeUnit(MICROSECONDS)
    public static class SingleThread {

        @Benchmark
        @OperationsPerInvocation(OPS_PER_BENCHMARK)
        public void benchmark(RuntimeState rs, Blackhole bh) {
            bh.consume(rs.run(forever(Fiber.<Unit>fiber(k -> k.accept(rs.cancelAfter(OPS_PER_BENCHMARK))))));
        }

        @State(Scope.Thread)
        public static class RuntimeState extends ForeverBenchmark.RuntimeState {
            @Override
            ExecutorService newExecutorService() {
                return newSingleThreadExecutor();
            }

        }

    }

    @BenchmarkMode(Throughput)
    @OutputTimeUnit(MICROSECONDS)
    public static class FixedPool {

        @Benchmark
        @OperationsPerInvocation(OPS_PER_BENCHMARK)
        public void benchmark(RuntimeState rs, Blackhole bh) {
            bh.consume(rs.run(forever(Fiber.<Unit>fiber(k -> k.accept(rs.cancelAfter(OPS_PER_BENCHMARK))))));
        }

        @State(Scope.Thread)
        public static class RuntimeState extends ForeverBenchmark.RuntimeState {
            @Override
            ExecutorService newExecutorService() {
                return newFixedThreadPool(getRuntime().availableProcessors());
            }

        }

    }

    @BenchmarkMode(Throughput)
    @OutputTimeUnit(MICROSECONDS)
    public static class ElasticPool {

        @Benchmark
        @OperationsPerInvocation(OPS_PER_BENCHMARK)
        public void benchmark(RuntimeState rs, Blackhole bh) {
            bh.consume(rs.run(forever(Fiber.<Unit>fiber(k -> k.accept(rs.cancelAfter(OPS_PER_BENCHMARK))))));
        }

        @State(Scope.Thread)
        public static class RuntimeState extends ForeverBenchmark.RuntimeState {
            @Override
            ExecutorService newExecutorService() {
                return newCachedThreadPool();
            }

        }

    }

    @BenchmarkMode(Throughput)
    @OutputTimeUnit(MICROSECONDS)
    public static class WorkStealingPool {

        @Benchmark
        @OperationsPerInvocation(OPS_PER_BENCHMARK)
        public void benchmark(RuntimeState rs, Blackhole bh) {
            bh.consume(rs.run(forever(Fiber.<Unit>fiber(k -> k.accept(rs.cancelAfter(OPS_PER_BENCHMARK))))));
        }

        @State(Scope.Thread)
        public static class RuntimeState extends ForeverBenchmark.RuntimeState {
            @Override
            ExecutorService newExecutorService() {
                return newWorkStealingPool();
            }

        }

    }

    @BenchmarkMode(Throughput)
    @OutputTimeUnit(MICROSECONDS)
    public static class ForkJoinPool {

        @Benchmark
        @OperationsPerInvocation(OPS_PER_BENCHMARK)
        public void benchmark(RuntimeState rs, Blackhole bh) {
            bh.consume(rs.run(forever(Fiber.<Unit>fiber(k -> k.accept(rs.cancelAfter(OPS_PER_BENCHMARK))))));
        }

        @State(Scope.Thread)
        public static class RuntimeState extends ForeverBenchmark.RuntimeState {
            @Override
            ExecutorService newExecutorService() {
                return commonPool();
            }

        }

    }
}
