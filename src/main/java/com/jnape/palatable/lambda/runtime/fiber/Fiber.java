package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;

import java.util.function.Consumer;

import static com.jnape.palatable.lambda.runtime.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;

//todo: seal
public interface Fiber<A> {

    void execute(Scheduler scheduler, Consumer<? super Result<? extends A>> callback);

    static <A> Fiber<A> result(Result<? extends A> result) {
        return (scheduler, callback) -> callback.accept(result);
    }

    static <A> Fiber<A> succeeded(A a) {
        return result(success(a));
    }

    static Fiber<Unit> succeeded() {
        return (scheduler, callback) -> callback.accept(success());
    }

    static <A> Fiber<A> failed(Throwable t) {
        return (scheduler, callback) -> callback.accept(failure(t));
    }

    static <A> Fiber<A> cancelled() {
        return (scheduler, callback) -> callback.accept(cancellation());
    }

    static <A> Fiber<A> never() {
        return (scheduler, callback) -> {};
    }

    static <A> Fiber<A> fiber(Fn0<? extends A> fn) {
        return (scheduler, callback) -> scheduler.schedule(() -> {
            Result<A> result;
            try {
                result = success(fn.apply());
            } catch (Throwable t) {
                result = failure(t);
            }
            callback.accept(result);
        });
    }
}