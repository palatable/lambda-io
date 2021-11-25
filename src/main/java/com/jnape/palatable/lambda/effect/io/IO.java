package com.jnape.palatable.lambda.effect.io;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;

import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;

public sealed interface IO<A> {

    <R> R interpret(Interpreter<A, R> interpreter);

    default A unsafePerformIO() {
        return IOPlatform.system().unsafeInterpretFully(this);
    }

    default <B> IO<B> ap(IO<Fn1<? super A, ? extends B>> ioF) {
        return new Parallel<>(this, ioF);
    }

    default <B> IO<B> bind(Fn1<? super A, ? extends IO<B>> f) {
        return new Sequential<>(this, f);
    }

    static <A> IO<A> io(A a) {
        return new Value<>(a);
    }

    static <A> IO<A> io(Fn0<? extends A> thunk) {
        return new Suspension<>(thunk);
    }

    static IO<Unit> io(SideEffect sideEffect) {
        return io(() -> {
            sideEffect.Ω();
            return UNIT;
        });
    }

    static <A> IO<A> io(Callback<? super Callback<? super A>> k) {
        return new Async<>(k);
    }

    static <A> IO<A> fork(Fn0<? extends A> thunk, Executor executor) {
        return io(k -> executor.execute(() -> k.call(thunk.apply())));
    }

    static IO<Unit> fork(SideEffect sideEffect, Executor executor) {
        return fork(() -> {
            sideEffect.Ω();
            return UNIT;
        }, executor);
    }
}

record Value<A>(A a) implements IO<A> {
    @Override
    public <R> R interpret(Interpreter<A, R> interpreter) {
        return interpreter.interpret(a);
    }
}

record Suspension<A>(Fn0<? extends A> thunk) implements IO<A> {
    @Override
    public <R> R interpret(Interpreter<A, R> interpreter) {
        return interpreter.interpret(thunk);
    }
}

record Async<A>(Callback<? super Callback<? super A>> k) implements IO<A> {
    @Override
    public <R> R interpret(Interpreter<A, R> interpreter) {
        return interpreter.interpret(k);
    }
}

record Parallel<Z, A>(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) implements IO<A> {
    @Override
    public <R> R interpret(Interpreter<A, R> interpreter) {
        return interpreter.interpret(ioZ, ioF);
    }
}

record Sequential<Z, A>(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) implements IO<A> {
    @Override
    public <R> R interpret(Interpreter<A, R> interpreter) {
        return interpreter.interpret(ioZ, f);
    }
}
