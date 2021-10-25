package com.jnape.palatable.lambda.benchmark;


import com.jnape.palatable.lambda.io.IO;

import static com.jnape.palatable.lambda.benchmark.Sample.sample;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

public final class Forever {

    public static void main(String[] args) {
//        unsafeRunSync(IO.forever(io(sample(100_000_000, MICROSECONDS)::mark)));
        forever(IO.io(sample(1_000_000, MICROSECONDS)::mark)).unsafePerformIO();
    }

    private static <A,B> IO<B> forever(IO<A> io)   {
        return io.flatMap(__ -> forever(io));
    }
}
