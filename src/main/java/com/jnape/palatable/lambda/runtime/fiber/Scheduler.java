package com.jnape.palatable.lambda.runtime.fiber;

import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.Executors.newScheduledThreadPool;

public interface Scheduler {

    void schedule(Runnable runnable);

    static Scheduler scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        return scheduledExecutorService::execute;
    }

    static Scheduler elastic() {
        return scheduledExecutorService(newScheduledThreadPool(0));
    }
}
