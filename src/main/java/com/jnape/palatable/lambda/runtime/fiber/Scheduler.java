package com.jnape.palatable.lambda.runtime.fiber;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public interface Scheduler {

    void schedule(Runnable runnable);

    static Scheduler scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        return scheduledExecutorService::execute;
    }

    static Scheduler elastic() {
        return scheduledExecutorService(new ScheduledThreadPoolExecutor(1));
    }
}
