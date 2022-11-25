package com.jnape.palatable.lambda.runtime.fiber;

@SuppressWarnings("unused")
public sealed interface Result<A> {

    record Success<A>(A value) implements Result<A> {
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
    }

    static <A> Success<A> success(A value) {
        return new Success<>(value);
    }

    static <A> Failure<A> failure(Throwable reason) {
        return new Failure<>(reason);
    }

    @SuppressWarnings("unchecked")
    static <A> Cancellation<A> cancellation() {
        return (Cancellation<A>) Cancellation.INSTANCE;
    }
}


