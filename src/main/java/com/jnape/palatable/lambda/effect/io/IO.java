package com.jnape.palatable.lambda.effect.io;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.effect.io.interpreter.RunSync;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;
import com.jnape.palatable.lambda.newIO.Callback;

import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;

public sealed interface IO<A> {

    <R> R interpret(Interpreter<A, R> interpreter);

    default <B> IO<B> ap(IO<Fn1<? super A, ? extends B>> ioF) {
        return new Parallel<>(this, ioF);
    }

    default <B> IO<B> bind(Fn1<? super A, ? extends IO<B>> f) {
        return new Sequential<>(this, f);
    }

    default A unsafePerformIO() {
        return RunSync.unsafeRunSync(this);
    }

    static <A> IO<A> io(A a) {
        return new Value<>(a);
    }

    static <A> IO<A> io(Fn0<? extends A> thunk) {
        return new Suspension<>(thunk);
    }

    public static void main(String[] args) {

        class Slot {
            long x = 0;
        }
        Slot slot = new Slot();
        forever(io(() -> {
            if (slot.x++ % 50_000_000 == 0)
                System.out.println(slot.x - 1);
        })).unsafePerformIO();

        times(10_000_000,
              io -> io.bind(x -> io(k -> {
                  if (x % 100_000 == 0)
                      System.out.println(x);
                  k.call(x + 1);
              })),
              IO.<Integer>io(k -> k.call(0)))
                .unsafePerformIO();
    }

    static <A, B> IO<B> forever(IO<A> io) {
        return io.bind(__ -> forever(io));
    }

    static IO<Unit> io(SideEffect sideEffect) {
        return io(() -> {
            sideEffect.Ω();
            return Unit.UNIT;
        });
    }

    static <A> IO<A> io(Callback<? super Callback<? super A>> k) {
        return new Async<>(k);
    }

    static <A> IO<A> fork(Fn0<? extends A> thunk, Executor executor) {
        return io(k -> executor.execute(() -> k.apply(thunk.apply())));
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
