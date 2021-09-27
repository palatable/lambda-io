package com.jnape.palatable.lambda.effect.io.interpreter;

sealed interface RecursiveCase<A, B> {
    record Inductive<A, B>(A a) implements RecursiveCase<A, B> {
    }

    record Terminal<A, B>(B b) implements RecursiveCase<A, B> {
    }
}
