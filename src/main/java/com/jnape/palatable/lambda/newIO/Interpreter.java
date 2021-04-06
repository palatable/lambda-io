package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;

public interface Interpreter<A, R> {

    R run(A a);

    R run(Fn0<A> thunk);

    <Z> R run(com.jnape.palatable.lambda.newIO.IO<Z> ioZ, com.jnape.palatable.lambda.newIO.IO<Fn1<? super Z, ? extends A>> ioF);

    <Z> R run(com.jnape.palatable.lambda.newIO.IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f);


}
