package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.effect.io.fiber.Trampoline.trampoline;

public interface Runtime {

    <A> void unsafeRunAsync(Fiber<A> fiber, Consumer<? super Result<A>> callback);

    static Runtime shared() {
        return trampoline(ForkJoinPool.commonPool()::execute);
    }
}

