package com.jnape.palatable.lambda.effect.io.fiber2.old;

import com.jnape.palatable.lambda.runtime.fiber.Result;

public interface FiberCallback<A> {
    void accept(Result<A> fiberResult);
}
