package com.jnape.palatable.lambda.runtime.fiber;

public interface Scheduler {

    void schedule(Runnable runnable);
}
