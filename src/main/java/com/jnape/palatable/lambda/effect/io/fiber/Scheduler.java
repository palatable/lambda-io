package com.jnape.palatable.lambda.effect.io.fiber;

public interface Scheduler {

    void schedule(Runnable runnable);
}
