package com.jnape.palatable.lambda.effect.io.fiber;

//todo: delay
public interface Scheduler {

    void schedule(Runnable runnable);
}
