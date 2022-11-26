package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.runtime.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;

//todo: should forever be its own Record?
public sealed interface Fiber<A> {

    void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<? extends A>> callback);

    static <A> Fiber<A> result(Result<? extends A> result) {
        return new Value<>(result);
    }

    static <A> Fiber<A> succeeded(A a) {
        return result(success(a));
    }

    static Fiber<Unit> succeeded() {
        return Value.SUCCESS_UNIT;
    }

    static <A> Fiber<A> failed(Throwable t) {
        return result(failure(t));
    }

    static <A> Fiber<A> cancelled() {
        return Value.cancelled();
    }

    static <A> Fiber<A> never() {
        return Never.instance();
    }

    static <A> Fiber<A> fiber(Supplier<? extends A> task) {
        return new Suspension<>(task);
    }

    static Fiber<Unit> fiber(Runnable action) {
        return fiber(() -> {
            action.run();
            return UNIT;
        });
    }
}

record Value<A>(Result<? extends A> result) implements Fiber<A> {
    static final Value<Unit> SUCCESS_UNIT = new Value<>(success());
    static final Value<?>    CANCELLED    = new Value<>(cancellation());

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<? extends A>> callback) {
        callback.accept(result);
    }

    @SuppressWarnings("unchecked")
    static <A> Value<A> cancelled() {
        return (Value<A>) CANCELLED;
    }
}

record Suspension<A>(Supplier<? extends A> task) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<? extends A>> callback) {
        if (!canceller.cancelled())
            scheduler.schedule(() -> {
                Result<A> result;
                if (canceller.cancelled())
                    result = cancellation();
                else
                    try {
                        result = success(task.get());
                    } catch (Throwable t) {
                        result = failure(t);
                    }
                callback.accept(result);
            });
        else
            callback.accept(cancellation());
    }
}

final class Never<A> implements Fiber<A> {
    static final Never<?> INSTANCE = new Never<>();

    private Never() {
    }

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<? extends A>> callback) {
    }

    @SuppressWarnings("unchecked")
    static <A> Never<A> instance() {
        return (Never<A>) INSTANCE;
    }
}