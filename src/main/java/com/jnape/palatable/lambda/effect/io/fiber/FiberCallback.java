package com.jnape.palatable.lambda.effect.io.fiber;

public interface FiberCallback<A> {
    void call(FiberResult<A> fiberResult);
}
