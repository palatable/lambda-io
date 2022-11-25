package com.jnape.palatable.lambda.effect.io.interpreter;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.effect.io.IO;
import com.jnape.palatable.lambda.effect.io.Interpreter;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.Cancelled;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.Failure;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.Success;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Recur;
import com.jnape.palatable.lambda.effect.io.interpreter.TailExpr.Return;
import com.jnape.palatable.lambda.functions.Fn1;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.effect.io.IO.io;
import static com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.success;

public final class RunSyncFiber<A> implements Interpreter<A, TailExpr<IO<A>, Unit>> {

    private final Consumer<? super FiberResult<A>> terminate;
    private final Consumer<? super IO<A>>          recur;

    private RunSyncFiber(Consumer<? super FiberResult<A>> terminate,
                         BiConsumer<? super Interpreter<A, TailExpr<IO<A>, Unit>>, ? super IO<A>> recur) {
        this.terminate = terminate;
        this.recur     = io -> recur.accept(this, io);
    }

    @Override
    public TailExpr<IO<A>, Unit> interpret(A a) {
        terminate.accept(success(a));
        return new Return<>(UNIT);
    }

    @Override
    public TailExpr<IO<A>, Unit> interpret(
            Consumer<? super Consumer<? super FiberResult<A>>> k) {
        k.accept(terminate);
        return new Return<>(UNIT);
    }

    @Override
    public <Z> TailExpr<IO<A>, Unit> interpret(
            IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
        return new Recur<>(ioZ.bind(z -> ioF.bind(f -> io(f.apply(z)))));
    }

    @Override
    public <Z> TailExpr<IO<A>, Unit> interpret(
            IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
        return ioZ.interpret(new Phi<>(f, terminate, recur));
    }

    private record Phi<Z, A>(
            Fn1<? super Z, ? extends IO<A>> f,
            Consumer<? super FiberResult<A>> terminate,
            Consumer<? super IO<A>> recur)
            implements Interpreter<Z, TailExpr<IO<A>, Unit>> {

        @Override
        public TailExpr<IO<A>, Unit> interpret(Z z) {
            return new Recur<>(f.apply(z));
        }

        @Override
        public TailExpr<IO<A>, Unit> interpret(
                Consumer<? super Consumer<? super FiberResult<Z>>> k) {
            k.accept((Consumer<? super FiberResult<Z>>) resZ -> {
                if (resZ instanceof Failure<Z> failureZ) {
                    terminate.accept(failureZ.contort());
                } else if (resZ instanceof Cancelled<Z> cancelled) {
                    terminate.accept(cancelled.contort());
                } else {
                    recur.accept(f.apply(((Success<Z>) resZ).result()));
                }
            });
            return new Return<>(UNIT);
        }

        @Override
        public <Y> TailExpr<IO<A>, Unit> interpret(
                IO<Y> ioY,
                IO<Fn1<? super Y, ? extends Z>> ioF) {
            return new Recur<>(ioY.bind(y -> ioF.bind(yz -> f.apply(yz.apply(y)))));
        }

        @Override
        public <Y> TailExpr<IO<A>, Unit> interpret(
                IO<Y> ioY,
                Fn1<? super Y, ? extends IO<Z>> g) {
            return new Recur<>(ioY.bind(y -> g.apply(y).bind(f)));
        }
    }

    public static <A> RunSyncFiber<A> runSyncFiber(Consumer<? super FiberResult<A>> termintae,
                                                   BiConsumer<? super Interpreter<A, TailExpr<IO<A>, Unit>>, ? super IO<A>> recur) {
        return new RunSyncFiber<>(termintae, recur);
    }
}
