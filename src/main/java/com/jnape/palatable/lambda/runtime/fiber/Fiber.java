package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;

import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.runtime.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;

public interface Fiber<A> {

    Fiber<Unit> SUCCEEDED_UNIT = succeeded(UNIT);

    void execute(Scheduler scheduler, Consumer<? super Result<? extends A>> callback);

    static <A> Fiber<A> fiber(Result<? extends A> result) {
        return (scheduler, callback) -> callback.accept(result);
    }

    static <A> Fiber<A> succeeded(A a) {
        return fiber(success(a));
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
}
