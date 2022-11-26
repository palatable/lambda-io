package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;

import java.util.function.Consumer;

import static com.jnape.palatable.lambda.runtime.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;

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

    static <A> Fiber<A> fiber(Fn0<? extends A> fn) {
        return new Suspension<>(fn);
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

record Suspension<A>(Fn0<? extends A> fn) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<? extends A>> callback) {
        if (!canceller.cancelled())
            scheduler.schedule(() -> {
                Result<A> result;
                if (canceller.cancelled())
                    result = cancellation();
                else
                    try {
                        result = success(fn.apply());
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