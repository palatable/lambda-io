package com.jnape.palatable.lambda.benchmark;

import com.jnape.palatable.lambda.adt.hlist.Tuple2;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.newIO.IO;
import com.jnape.palatable.lambda.semigroup.Semigroup;

import static com.jnape.palatable.lambda.adt.hlist.HList.tuple;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.newIO.IO.io;
import static com.jnape.palatable.lambda.semigroup.builtin.Collapse.collapse;

public final class FlatMap {
    private FlatMap() {
    }

    public static void main(String[] args) {
        int samples = 10_000_000;
        System.out.println("Samples: " + samples);

        Tuple2<Long, Long> oldTimings = benchmark(
                "Sync: Old FlatMap",
                com.jnape.palatable.lambda.io.IO.io(() -> 0),
                io -> io.flatMap(x -> com.jnape.palatable.lambda.io.IO.io(x + 1)),
                com.jnape.palatable.lambda.io.IO::unsafePerformIO,
                samples);
        Tuple2<Long, Long> newTimings = benchmark(
                "Sync: New FlatMap",
                io(() -> 0),
                io -> io.bind(x -> io(x + 1)),
                IO::unsafePerformIO,
                samples);

        printComparison(oldTimings, newTimings);
    }

    private static void printComparison(Tuple2<Long, Long> oldTimings, Tuple2<Long, Long> newTimings) {
        Semigroup<Long>    compareOldAndNew = (oldM, newM) -> (newM / oldM) * 100;
        Tuple2<Long, Long> percentDiff      = collapse(compareOldAndNew, compareOldAndNew, oldTimings, newTimings);

        System.out.println("Build new-vs-old: " + percentDiff._1() + "%");
        System.out.println("Run new-vs-old: " + percentDiff._2() + "%");
    }

    public static <IO> Tuple2<Long, Long> benchmark(String label, IO io, Fn1<IO, IO> build, Fn1<IO, Object> run,
                                                    int samples) {
        System.gc();
        System.out.println("=== " + label + " ===");
        System.out.print("Building...");
        long startBuildMs = System.currentTimeMillis();
        IO   built        = times(samples, build, io);
        long totalBuildMs = System.currentTimeMillis() - startBuildMs;
        System.out.print("built (" + (samples / totalBuildMs) + " ops/ms). Running...");
        long   startRunMs = System.currentTimeMillis();
        Object result     = run.apply(built);
        long   totalRunMs = System.currentTimeMillis() - startRunMs;
        System.out.println("finished: " + result + " (" + (samples / totalRunMs) + " ops/ms).");
        return tuple(samples / totalBuildMs, samples / totalRunMs);
    }
}
