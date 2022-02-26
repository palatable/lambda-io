package com.jnape.palatable.lambda.effect.io.interpreter;

import com.jnape.palatable.lambda.effect.io.IO;
import com.jnape.palatable.lambda.effect.io.Interpreter;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.Cancelled;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.Failure;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Recur;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Return;
import com.jnape.palatable.lambda.functions.Fn1;

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.effect.io.IO.io;
import static com.jnape.palatable.lambda.internal.Runtime.throwChecked;

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
    public TailExpr<IO<A>, A> interpret(Consumer<? super Consumer<? super FiberResult<A>>> k) {
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
        public IO<A> interpret(Consumer<? super Consumer<? super FiberResult<Z>>> k) {
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

    private static <A> A fireAndAwait(Consumer<? super Consumer<? super FiberResult<A>>> k) {
        FiberResult<A> result = new Object() {
            volatile FiberResult<A> value;
            volatile boolean complete = false;

            {
                synchronized (this) {
                    k.accept((Consumer<? super FiberResult<A>>) resA -> {
                        synchronized (this) {
                            value    = resA;
                            complete = true;
                            notify();
                        }
                    });
                    while (!complete) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            throw throwChecked(e);
                        }
                    }
                }
            }
        }.value;

        if (result instanceof Cancelled<?>)
            throw new CancellationException();
        else if (result instanceof Failure<?> failure)
            throw throwChecked(failure.ex());
        return ((FiberResult.Success2<A>) result).result();
    }

    @SuppressWarnings("unchecked")
    public static <A> RunSync<A> runSync() {
        return (RunSync<A>) INSTANCE;
    }
}
