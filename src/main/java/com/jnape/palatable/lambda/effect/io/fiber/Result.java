package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;

@SuppressWarnings("unused")
public sealed interface Result<A> {

    static <A> Success<A> success(A value) {
        if (value == UNIT) {
            @SuppressWarnings("unchecked")
            Success<A> successUnit = (Success<A>) success();
            return successUnit;
        }
        return new Success<>(value);
    }

    static Success<Unit> success() {
        return Success.SUCCESS_UNIT;
    }

    static <A> Failure<A> failure(Throwable reason) {
        return new Failure<>(reason);
    }

    @SuppressWarnings("unchecked")
    static <A> Cancellation<A> cancellation() {
        return (Cancellation<A>) Cancellation.INSTANCE;
    }

    sealed interface Unsuccessful<A> extends Result<A> {
        <B> Unsuccessful<B> contort();
    }

    record Success<A>(A value) implements Result<A> {
        private static final Success<Unit> SUCCESS_UNIT = new Success<>(UNIT);
    }

    record Failure<A>(Throwable reason) implements Unsuccessful<A> {

        @SuppressWarnings("unchecked")
        public <B> Failure<B> contort() {
            return (Failure<B>) this;
        }
    }

    final class Cancellation<A> implements Unsuccessful<A> {
        private static final Cancellation<?> INSTANCE = new Cancellation<>();

        private Cancellation() {
        }

        @SuppressWarnings("unchecked")
        public <B> Cancellation<B> contort() {
            return (Cancellation<B>) this;
        }

        @Override
        public String toString() {
            return "Cancellation[]";
        }
    }
}


