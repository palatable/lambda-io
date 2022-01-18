package com.jnape.palatable.lambda.effect.io.fiber2.old;

public interface FiberCallback<A> {
    void call(FiberResult<A> fiberResult);
}
