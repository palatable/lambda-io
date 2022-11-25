package com.jnape.palatable.lambda.runtime.fiber;

import java.util.concurrent.ScheduledExecutorService;

public interface Scheduler {

    void schedule(Runnable runnable);

    static Scheduler scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        return scheduledExecutorService::execute;
    }
}
