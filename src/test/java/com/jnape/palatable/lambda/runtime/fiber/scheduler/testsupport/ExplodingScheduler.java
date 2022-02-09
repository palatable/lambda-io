package com.jnape.palatable.lambda.runtime.fiber.scheduler.testsupport;

import com.jnape.palatable.lambda.runtime.fiber.Canceller;
import com.jnape.palatable.lambda.runtime.fiber.Scheduler;

import java.time.Duration;

public final class ExplodingScheduler implements Scheduler {
    private static final ExplodingScheduler INSTANCE = new ExplodingScheduler();

    private ExplodingScheduler() {
    }

    @Override
    public void schedule(Runnable runnable) {
        throw new UnsupportedOperationException("schedule(Runnable)");
    }

    @Override
    public void schedule(Duration delay, Runnable runnable, Canceller canceller) {
        throw new UnsupportedOperationException("schedule(Duration, Runnable, Canceller)");
    }

    public static ExplodingScheduler explodingScheduler() {
        return INSTANCE;
    }
}
