package com.jnape.palatable.lambda.benchmark;


import com.jnape.palatable.lambda.effect.io.IO;

import static com.jnape.palatable.lambda.benchmark.Sample.sample;
import static com.jnape.palatable.lambda.effect.io.IO.io;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class Forever {

    public static void main2(String[] args) {
        forever(io(sample(100_000_000_000L, MICROSECONDS)::mark)).unsafePerformIO();
    }

    static <A, B> IO<B> forever(IO<A> io) {
        return io.bind(__ -> forever(io));
    }

    public static void main(String[] args) {
        Sample sample = sample(1_000_000_000L, NANOSECONDS);
        Runnable r = () -> sample.mark();
        while (true) {
            r.run();
        }
    }
}
