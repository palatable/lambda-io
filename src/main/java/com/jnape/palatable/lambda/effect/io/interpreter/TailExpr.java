package com.jnape.palatable.lambda.effect.io.interpreter;

@SuppressWarnings("unused")
public sealed interface TailExpr<A, B> {
    record Recur<A, B>(A a) implements TailExpr<A, B> {
    }

    record Return<A, B>(B b) implements TailExpr<A, B> {
    }
}
