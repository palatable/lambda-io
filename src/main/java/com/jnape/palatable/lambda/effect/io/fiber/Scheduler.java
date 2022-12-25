package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.Executor;

public interface Scheduler {

    void schedule(Runnable runnable);

    static Scheduler scheduler(Executor executor) {
        return executor::execute;
    }
}
