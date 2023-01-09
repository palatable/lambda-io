package com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler;

import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;

import java.util.function.Consumer;

public final class DecoratingScheduler implements Scheduler {

    private final Scheduler          delegate;
    private final Consumer<Runnable> before;
    private final Consumer<Runnable> after;

    private DecoratingScheduler(Scheduler delegate, Consumer<Runnable> before, Consumer<Runnable> after) {
        this.delegate = delegate;
        this.before   = before;
        this.after    = after;
    }

    @Override
    public void schedule(Runnable runnable) {
        before.accept(runnable);
        delegate.schedule(runnable);
        after.accept(runnable);
    }

    public static DecoratingScheduler around(Scheduler scheduler, Consumer<Runnable> before, Consumer<Runnable> after) {
        return new DecoratingScheduler(scheduler, before, after);
    }

    public static DecoratingScheduler before(Scheduler scheduler, Consumer<Runnable> before) {
        return around(scheduler, before, __ -> {});
    }

    public static DecoratingScheduler after(Scheduler scheduler, Consumer<Runnable> after) {
        return around(scheduler, __ -> {}, after);
    }
}
