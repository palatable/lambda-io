package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;

public interface Runtime {

    <A> void unsafeRunAsync(Fiber<A> fiber, Consumer<? super Result<A>> callback);

    static Runtime shared() {
        return new Trampoline(ForkJoinPool.commonPool()::execute);
    }
}

