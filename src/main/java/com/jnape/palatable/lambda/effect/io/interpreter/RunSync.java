package com.jnape.palatable.lambda.effect.io.interpreter;

import com.jnape.palatable.lambda.effect.io.IO;
import com.jnape.palatable.lambda.effect.io.Interpreter;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Recur;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Return;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.internal.Runtime;
import com.jnape.palatable.lambda.effect.io.Callback;

import static com.jnape.palatable.lambda.effect.io.IO.io;

//todo: interpret into arrow accepting platform to inject console etc.
public final class RunSync<A> implements Interpreter<A, TailExpr<IO<A>, A>> {

    private static final RunSync<?> INSTANCE = new RunSync<>();

    private RunSync() {
    }

    @Override
    public TailExpr<IO<A>, A> interpret(A a) {
        return new Return<>(a);
    }

    @Override
    public TailExpr<IO<A>, A> interpret(Fn0<? extends A> thunk) {
        return new Return<>(thunk.apply());
    }

    @Override
    public TailExpr<IO<A>, A> interpret(Callback<? super Callback<? super A>> k) {
        return new Return<>(fireAndAwait(k));
    }

    @Override
    public <Z> TailExpr<IO<A>, A> interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
        return new Recur<>(ioZ.bind(z -> ioF.bind(f -> io(f.apply(z)))));
    }

    @Override
    public <Z> TailExpr<IO<A>, A> interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
        return new Recur<>(ioZ.interpret(new Phi<>(f)));
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
                        synchronized (this) {
                            value    = a;
                            complete = true;
                            notify();
                        }
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
