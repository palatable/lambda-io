package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.runtime.fiber.Result.Cancelled;
import com.jnape.palatable.lambda.runtime.fiber.Result.Successful;

public sealed interface Result<A> {

    boolean isCancelled();

    sealed interface Successful<A> extends Result<A> {
        A value();

        @Override
        default boolean isCancelled() {
            return false;
        }
    }

    sealed interface Unsuccessful<A> extends Result<A> {
        @SuppressWarnings("unchecked")
        default <B> Unsuccessful<B> contort() {
            return (Unsuccessful<B>) this;
        }
    }

    sealed interface Failed<A> extends Unsuccessful<A> {
        Throwable reason();

        @Override
        default boolean isCancelled() {
            return false;
        }

        @Override
        default <B> Failed<B> contort() {
            return (Failed<B>) Unsuccessful.super.contort();
        }
    }

    sealed interface Cancelled<A> extends Unsuccessful<A> {

        @Override
        default boolean isCancelled() {
            return true;
        }

        @Override
        default <B> Cancelled<B> contort() {
            return (Cancelled<B>) Unsuccessful.super.contort();
        }
    }

    static <A> Successful<A> successful(A value) {
        return new Success<>(value);
    }

    static <A> Failed<A> failed(Throwable reason) {
        return new Failure<>(reason);
    }

    @SuppressWarnings("unchecked")
    static <A> Cancelled<A> cancelled() {
        return (Cancelled<A>) Cancellation.INSTANCE;
    }
}

record Success<A>(A value) implements Successful<A> {
}

record Failure<A>(Throwable reason) implements Result.Failed<A> {
}

final class Cancellation<A> implements Cancelled<A> {
    static final Cancellation<?> INSTANCE = new Cancellation<>();
}