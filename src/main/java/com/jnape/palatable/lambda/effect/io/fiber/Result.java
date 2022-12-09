package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;

@SuppressWarnings("unused")
public sealed interface Result<A> {

    static <A> Success<A> success(A value) {
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

    record Success<A>(A value) implements Result<A> {
        private static final Success<Unit> SUCCESS_UNIT = success(UNIT);
    }

    record Failure<A>(Throwable reason) implements Result<A> {

        @SuppressWarnings("unchecked")
        public <B> Failure<B> contort() {
            return (Failure<B>) this;
        }
    }

    final class Cancellation<A> implements Result<A> {
        private static final Cancellation<?> INSTANCE = new Cancellation<>();

        private Cancellation() {
        }

        @Override
        public String toString() {
            return "Cancellation[]";
        }
    }
}

