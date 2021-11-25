package com.jnape.palatable.lambda.effect.io.interpreter;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.effect.io.Callback;
import com.jnape.palatable.lambda.functions.Fn0;

public interface LeafInterpreter<A, R> {

    R interpret(A a);

    <X> Either<X, R> interpretE(X x);

    R interpret(Fn0<? extends A> thunk);

    <X> Either<X, R> interpretE(Fn0<? extends X> thunk);

    R interpret(Callback<? super Callback<? super A>> k);

    <X> Either<X, R> interpretE(Callback<? super Callback<? super X>> k);
}
