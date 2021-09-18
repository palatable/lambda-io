package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;

public interface Interpreter<A, R> {

    R interpret(A a);

    R interpret(Fn0<A> thunk);

    R interpret(Callback<? super Callback<? super A>> k);

    <Z> R interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF);

    <Z> R interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f);
}
