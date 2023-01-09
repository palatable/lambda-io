package com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class DecoratingScheduler implements Executor {

    private final Executor           delegate;
    private final Consumer<Runnable> before;
    private final Consumer<Runnable> after;

    private DecoratingScheduler(Executor delegate, Consumer<Runnable> before, Consumer<Runnable> after) {
        this.delegate = delegate;
        this.before   = before;
        this.after    = after;
    }

    @Override
    public void execute(Runnable runnable) {
        before.accept(runnable);
        delegate.execute(runnable);
        after.accept(runnable);
    }

    public static DecoratingScheduler around(Executor executor, Consumer<Runnable> before, Consumer<Runnable> after) {
        return new DecoratingScheduler(executor, before, after);
    }

    public static DecoratingScheduler before(Executor executor, Consumer<Runnable> before) {
        return around(executor, before, __ -> {});
    }

    public static DecoratingScheduler after(Executor executor, Consumer<Runnable> after) {
        return around(executor, __ -> {}, after);
    }
}
