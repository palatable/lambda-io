package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;

import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.runtime.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;

public interface Fiber<A> {

    Fiber<Unit> SUCCEEDED_UNIT = succeeded(UNIT);
    Fiber<?>    NEVER          = (scheduler, callback) -> {};

    void execute(Scheduler scheduler, Consumer<? super Result<? extends A>> callback);

    static <A> Fiber<A> result(Result<? extends A> result) {
        return (scheduler, callback) -> callback.accept(result);
    }

    static <A> Fiber<A> succeeded(A a) {
        return result(success(a));
    }

    static Fiber<Unit> succeeded() {
        return SUCCEEDED_UNIT;
    }

    static <A> Fiber<A> failed(Throwable t) {
        return (scheduler, callback) -> callback.accept(failure(t));
    }

    static <A> Fiber<A> cancelled() {
        return (scheduler, callback) -> callback.accept(cancellation());
    }

    @SuppressWarnings("unchecked")
    static <A> Fiber<A> never() {
        return (Fiber<A>) NEVER;
    }
}
