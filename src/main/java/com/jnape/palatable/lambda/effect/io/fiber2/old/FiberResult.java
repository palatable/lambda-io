package com.jnape.palatable.lambda.effect.io.fiber2.old;

public sealed interface FiberResult<A> {

    static <A> FiberResult<A> success(A a) {
        return new Success2<>(a);
    }

    @SuppressWarnings("unchecked")
    static <A> FiberResult<A> cancelled() {
        return (FiberResult<A>) Cancelled.INSTANCE;
    }

    static <A> FiberResult<A> failure(Throwable t) {
        return new Failure<>(t);
    }

    sealed interface NonCancelledResult<A> extends FiberResult<A> {
    }

    sealed interface UnsuccessfulResult<A> extends FiberResult<A> {
        @SuppressWarnings("unchecked")
        default <B> UnsuccessfulResult<B> contort() {
            return (UnsuccessfulResult<B>) this;
        }
    }

    record Success2<A>(A result) implements NonCancelledResult<A> {

        @Override
        public String toString() {
            return "FiberResult[Success{" +
                    "result=" + result +
                    "}]";
        }
    }

    record Failure<A>(Throwable ex) implements NonCancelledResult<A>, UnsuccessfulResult<A> {

        @Override
        public <B> Failure<B> contort() {
            return (Failure<B>) UnsuccessfulResult.super.contort();
        }

        @Override
        public String toString() {
            return "FiberResult[Failure{" +
                    "ex=" + ex +
                    "}]";
        }
    }

    final class Cancelled<A> implements FiberResult<A>, UnsuccessfulResult<A> {
        private static final Cancelled<?> INSTANCE = new Cancelled<>();

        private Cancelled() {
        }

        @Override
        public String toString() {
            return "FiberResult[Cancelled]";
        }
    }
}
