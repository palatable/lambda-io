package com.jnape.palatable.lambda.effect.io.fiber2.old;

import com.jnape.palatable.lambda.functions.Fn1;

public sealed interface FiberResult<A> {

    <B> FiberResult<B> mapResult(Fn1<? super NonCancelledResult<A>, ? extends NonCancelledResult<B>> f);

    <B> FiberResult<B> map(Fn1<? super A, ? extends B> fn);

    static <A> FiberResult<A> success(A a) {
        return new Success<>(a);
    }

    @SuppressWarnings("unchecked")
    static <A> FiberResult<A> cancelled() {
        return (FiberResult<A>) Cancelled.INSTANCE;
    }

    static <A> FiberResult<A> failure(Throwable t) {
        return new Failure<>(t);
    }

    sealed interface NonCancelledResult<A> extends FiberResult<A> {
        @Override
        default <B> NonCancelledResult<B> mapResult(
                Fn1<? super NonCancelledResult<A>, ? extends NonCancelledResult<B>> f) {
            return f.apply(this);
        }
    }

    sealed interface UnsuccessfulResult<A> extends FiberResult<A> {
        @SuppressWarnings("unchecked")
        default <B> UnsuccessfulResult<B> contort() {
            return (UnsuccessfulResult<B>) this;
        }
    }

    record Success<A>(A result) implements NonCancelledResult<A> {
        @Override
        public <B> FiberResult<B> map(Fn1<? super A, ? extends B> fn) {
            return new Success<>(fn.apply(result));
        }

        @Override
        public String toString() {
            return "FiberResult[Success{" +
                    "result=" + result +
                    "}]";
        }
    }

    record Failure<A>(Throwable ex) implements NonCancelledResult<A>, UnsuccessfulResult<A> {
        @Override
        public <B> FiberResult<B> map(Fn1<? super A, ? extends B> fn) {
            return contort();
        }

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

        @Override
        @SuppressWarnings("unchecked")
        public <B> FiberResult<B> mapResult(Fn1<? super NonCancelledResult<A>, ? extends NonCancelledResult<B>> f) {
            return (FiberResult<B>) this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <B> FiberResult<B> map(Fn1<? super A, ? extends B> fn) {
            return (FiberResult<B>) this;
        }

        private Cancelled() {
        }

        @Override
        public String toString() {
            return "FiberResult[Cancelled]";
        }
    }
}
