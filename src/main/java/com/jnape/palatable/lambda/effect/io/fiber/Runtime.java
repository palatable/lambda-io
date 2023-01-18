package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface Runtime {

    <A> void unsafeRunAsync(Fiber<A> fiber, Consumer<? super Result<A>> callback);

    default <A> Result<A> unsafeRunSync(Fiber<A> fiber) {
        return new CompletableFuture<Result<A>>() {{
            unsafeRunAsync(fiber, this::complete);
        }}.join();
    }
}

