package com.jnape.palatable.lambda.effect.io;

import com.jnape.palatable.lambda.adt.Unit;

import static com.jnape.palatable.lambda.effect.io.IO.io;

public interface Console {
    IO<Unit> println(Object message);

    IO<Unit> printlnErr(Object message);

    final class SystemConsole implements Console {
        @Override
        public IO<Unit> println(Object message) {
            return io(() -> System.out.println(message));
        }

        @Override
        public IO<Unit> printlnErr(Object message) {
            return io(() -> System.err.println(message));
        }
    }
}
