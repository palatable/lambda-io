package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;

import java.util.function.Consumer;

import static com.jnape.palatable.lambda.runtime.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;

public sealed interface Fiber<A> {

    void execute(Scheduler scheduler, Consumer<? super Result<? extends A>> callback);

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
        return new Failed<>(t);
    }

    static <A> Fiber<A> cancelled() {
        return Cancelled.instance();
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

    @Override
    public void execute(Scheduler scheduler, Consumer<? super Result<? extends A>> callback) {
        callback.accept(result);
    }
}

record Failed<A>(Throwable reason) implements Fiber<A> {

    @Override
    public void execute(Scheduler scheduler, Consumer<? super Result<? extends A>> callback) {
        callback.accept(failure(reason));
    }
}

final class Cancelled<A> implements Fiber<A> {
    static final Cancelled<?> INSTANCE = new Cancelled<>();

    private Cancelled() {
    }

    @Override
    public void execute(Scheduler scheduler, Consumer<? super Result<? extends A>> callback) {
        callback.accept(cancellation());
    }

    @SuppressWarnings("unchecked")
    static <A> Cancelled<A> instance() {
        return (Cancelled<A>) INSTANCE;
    }
}

final class Never<A> implements Fiber<A> {
    static final Never<?> INSTANCE = new Never<>();

    private Never() {
    }

    @Override
    public void execute(Scheduler scheduler, Consumer<? super Result<? extends A>> callback) {
    }

    @SuppressWarnings("unchecked")
    static <A> Never<A> instance() {
        return (Never<A>) INSTANCE;
    }
}

record Suspension<A>(Fn0<? extends A> fn) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Consumer<? super Result<? extends A>> callback) {
        scheduler.schedule(() -> {
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