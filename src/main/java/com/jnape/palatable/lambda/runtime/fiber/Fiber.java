package com.jnape.palatable.lambda.runtime.fiber;

import research.lambda.runtime.fiber.Result;

import java.util.function.Consumer;

public interface Fiber<A> {

    void execute(Scheduler scheduler, Consumer<? super Result<A>> callback);
}
