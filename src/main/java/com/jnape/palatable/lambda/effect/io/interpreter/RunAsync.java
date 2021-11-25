package com.jnape.palatable.lambda.effect.io.interpreter;

import com.jnape.palatable.lambda.effect.io.Callback;
import com.jnape.palatable.lambda.effect.io.IO;
import com.jnape.palatable.lambda.effect.io.Interpreter;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Recur;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Return;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;

import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.effect.io.IO.io;

public final class RunAsync<A> implements Interpreter<A, TailExpr<IO<A>, Callback<? super Callback<? super A>>>> {

    private final Executor executor;

    private RunAsync(Executor executor) {
        this.executor = executor;
    }

    public Callback<? super Callback<? super A>> resume(IO<A> io) {
        TailExpr<IO<A>, Callback<? super Callback<? super A>>> next = new Recur<>(io);
        while (next instanceof Recur<IO<A>, Callback<? super Callback<? super A>>> i) {
            next = i.a().interpret(this);
        }
        return ((Return<IO<A>, Callback<? super Callback<? super A>>>) next).b();
    }

    @Override
    public TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(A a) {
        return new Return<>(k -> k.apply(a));
    }

    @Override
    public TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(Fn0<? extends A> thunk) {
        return new Return<>(k -> executor.execute(() -> k.call(thunk.apply())));
    }

    @Override
    public TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(
            Callback<? super Callback<? super A>> k) {
        return new Return<>(k);
    }

    @Override
    public <Z> TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(IO<Z> ioZ,
                                                                                IO<Fn1<? super Z, ? extends A>> ioF) {
        return ioZ.interpret(new Phi<>(z -> ioF.bind(f -> io(f.apply(z))), executor));
    }

    @Override
    public <Z> TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(IO<Z> ioZ,
                                                                                Fn1<? super Z, ? extends IO<A>> f) {
        return ioZ.interpret(new Phi<>(f, executor));
    }

    private record Phi<Z, A>(Fn1<? super Z, ? extends IO<A>> f, Executor executor)
            implements Interpreter<Z, TailExpr<IO<A>, Callback<? super Callback<? super A>>>> {

        @Override
        public TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(Z z) {
            return new Recur<>(f.apply(z));
        }

        @Override
        public TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(Fn0<? extends Z> thunk) {
            return new Return<>(k -> executor.execute(() -> RunAsync.<A>runAsync(executor).resume(f.apply(thunk.apply())).call(k)));
        }

        @Override
        public TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(
                Callback<? super Callback<? super Z>> k) {
            return new Return<>(k2 -> k.call((Callback<? super Z>) z -> RunAsync.<A>runAsync(executor).resume(f.apply(z)).call(k2)));
        }

        @Override
        public <Y> TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(IO<Y> ioY,
                                                                                    IO<Fn1<? super Y, ? extends Z>> ioF) {
            return new Recur<>(ioY.bind(y -> ioF.bind(yz -> f.apply(yz.apply(y)))));
        }

        @Override
        public <Y> TailExpr<IO<A>, Callback<? super Callback<? super A>>> interpret(IO<Y> ioY,
                                                                                    Fn1<? super Y, ? extends IO<Z>> g) {
            return new Recur<>(ioY.bind(y -> g.apply(y).bind(f)));
        }
    }

    public static <A> RunAsync<A> runAsync(Executor executor) {
        return new RunAsync<>(executor);
    }
}
