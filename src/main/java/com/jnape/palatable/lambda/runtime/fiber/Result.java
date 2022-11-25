package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;

@SuppressWarnings("unused")
public sealed interface Result<A> {

    record Successful<A>(A value) implements Result<A> {
        private static final Successful<Unit> SUCCESS_UNIT = new Successful<>(UNIT);
    }

    record Failed<A>(Throwable reason) implements Result<A> {

        @SuppressWarnings("unchecked")
        public <B> Failed<B> contort() {
            return (Failed<B>) this;
        }
    }

    final class Cancelled<A> implements Result<A> {
        private static final Cancelled<?> INSTANCE = new Cancelled<>();

        @SuppressWarnings("unchecked")
        public <B> Cancelled<B> contort() {
            return (Cancelled<B>) this;
        }
    }

    static <A> Successful<A> successful(A value) {
        return new Successful<>(value);
    }

    static Successful<Unit> successful() {
        return Successful.SUCCESS_UNIT;
    }

    static <A> Failed<A> failed(Throwable reason) {
        return new Failed<>(reason);
    }

    @SuppressWarnings("unchecked")
    static <A> Cancelled<A> cancelled() {
        return (Cancelled<A>) Cancelled.INSTANCE;
    }
}


