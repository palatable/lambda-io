package com.jnape.palatable.lambda.effect.io.interpreter;

import com.jnape.palatable.lambda.effect.io.IO;
import com.jnape.palatable.lambda.effect.io.Interpreter;
import com.jnape.palatable.lambda.effect.io.interpreter.RecursiveCase.Inductive;
import com.jnape.palatable.lambda.effect.io.interpreter.RecursiveCase.Terminal;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.internal.Runtime;
import com.jnape.palatable.lambda.effect.io.Callback;

import static com.jnape.palatable.lambda.effect.io.IO.io;

public final class RunSync<A> implements Interpreter<A, RecursiveCase<IO<A>, A>> {

    private static final RunSync<?> INSTANCE = new RunSync<>();

    private RunSync() {
    }

    public static <A> A unsafeRunSync(IO<A> io) {
        RunSync<A>              instance = runSync();
        RecursiveCase<IO<A>, A> next     = new Inductive<>(io);
        while (next instanceof Inductive<IO<A>, A> i) {
            next = i.a().interpret(instance);
        }
        return ((Terminal<IO<A>, A>) next).b();
    }

    @Override
    public RecursiveCase<IO<A>, A> interpret(A a) {
        return new Terminal<>(a);
    }

    @Override
    public RecursiveCase<IO<A>, A> interpret(Fn0<? extends A> thunk) {
        return new Terminal<>(thunk.apply());
    }

    @Override
    public RecursiveCase<IO<A>, A> interpret(Callback<? super Callback<? super A>> k) {
        return new Terminal<>(fireAndAwait(k));
    }

    @Override
    public <Z> RecursiveCase<IO<A>, A> interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
        return new Inductive<>(ioZ.bind(z -> ioF.bind(f -> io(f.apply(z)))));
    }

    @Override
    public <Z> RecursiveCase<IO<A>, A> interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
        return new Inductive<>(ioZ.interpret(new Phi<>(f)));
    }

    private record Phi<Z, A>(
            Fn1<? super Z, ? extends IO<A>> f) implements Interpreter<Z, IO<A>> {

        @Override
        public IO<A> interpret(Z z) {
            return f.apply(z);
        }

        @Override
        public IO<A> interpret(Fn0<? extends Z> thunk) {
            return f.apply(thunk.apply());
        }

        @Override
        public IO<A> interpret(Callback<? super Callback<? super Z>> k) {
            return f.apply(fireAndAwait(k));
        }

        @Override
        public <Y> IO<A> interpret(IO<Y> ioY, IO<Fn1<? super Y, ? extends Z>> ioF) {
            return ioY.bind(y -> ioF.bind(yz -> f.apply(yz.apply(y))));
        }

        @Override
        public <Y> IO<A> interpret(IO<Y> ioY, Fn1<? super Y, ? extends IO<Z>> g) {
            return ioY.bind(y -> g.apply(y).bind(f));
        }
    }

    private static <A> A fireAndAwait(Callback<? super Callback<? super A>> k) {
        return new Object() {
            volatile A value;
            volatile boolean complete = false;

            {
                synchronized (this) {
                    k.call((Callback<? super A>) a -> {
                        value    = a;
                        complete = true;
                        notify();
                    });
                    while (!complete) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            throw Runtime.throwChecked(e);
                        }
                    }
                }
            }
        }.value;
    }

    @SuppressWarnings("unchecked")
    public static <A> RunSync<A> runSync() {
        return (RunSync<A>) INSTANCE;
    }
}
