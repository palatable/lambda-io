package com.jnape.palatable.lambda.benchmark;


import com.jnape.palatable.lambda.effect.io.IO;
import com.jnape.palatable.lambda.effect.io.IOPlatform;
import com.jnape.palatable.lambda.effect.io.fiber.Cancel;
import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import com.jnape.palatable.lambda.effect.io.interpreter.Fiber;

import static com.jnape.palatable.lambda.benchmark.Sample.sample;
import static com.jnape.palatable.lambda.effect.io.IO.io;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

public final class Forever {

    public static void main(String[] args) {
        IOPlatform.system().unsafeRun(forever(io(sample("a", 100_000_000L, MICROSECONDS)::mark)), System.out::println);
    }

    public static <A, B> IO<B> forever(IO<A> io) {
        return io.bind(__ -> forever(io));
    }
}
