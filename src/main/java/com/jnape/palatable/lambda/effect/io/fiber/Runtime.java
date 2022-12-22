package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.function.Consumer;

public interface Runtime {

    <A> void unsafeRunAsync(Fiber<A> fiber, Consumer<? super Result<A>> callback);
}

