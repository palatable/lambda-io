package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.effect.io.Callback;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.util.concurrent.CompletableFuture;

import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static com.jnape.palatable.lambda.effect.io.Callback.callback;
import static com.jnape.palatable.lambda.newIO.IO.io;

public final class UnsafeRunSync<A> implements Interpreter<A, A> {

    private static final UnsafeRunSync<?> INSTANCE = new UnsafeRunSync<>();

    private UnsafeRunSync() {
    }

    @Override
    public A interpret(A a) {
        return a;
    }

    @Override
    public A interpret(Fn0<A> thunk) {
        return thunk.apply();
    }

    @Override
    public A interpret(Callback<? super Callback<? super A>> k) {
        return await(k);
    }

    private static <A> A await(Callback<? super Callback<? super A>> k) {
        return new CompletableFuture<A>() {{
            k.apply(callback(this::complete));
        }}.join();
    }

    @Override
    public <Z> A interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
        Z z = unsafeRun(ioZ);
        return unsafeRun(ioF).apply(z);
    }

    @Override
    public <Z> A interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
        return unsafeRun(f.apply(unsafeRun(ioZ)));
    }

    private static <A> A unsafeRun(IO<A> io) {
        Phi<A> phi = Phi.phi();
        return trampoline(io_ -> io_.interpret(phi), io);
    }

    private static final class Phi<A> implements Interpreter<A, RecursiveResult<IO<A>, A>> {

        private static final Phi<?> INSTANCE = new Phi<>();

        @SuppressWarnings({"unchecked"})
        public static <A> Phi<A> phi() {
            return (Phi<A>) INSTANCE;
        }

        @Override
        public RecursiveResult<IO<A>, A> interpret(A a) {
            return terminate(a);
        }

        @Override
        public RecursiveResult<IO<A>, A> interpret(Fn0<A> thunk) {
            return terminate(thunk.apply());
        }

        @Override
        public RecursiveResult<IO<A>, A> interpret(Callback<? super Callback<? super A>> k) {
            return terminate(await(k));
        }

        @Override
        public <Z> RecursiveResult<IO<A>, A> interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
            return recurse(ioZ.bind(z -> ioF.bind(f -> io(f.apply(z)))));
        }

        @Override
        public <Z> RecursiveResult<IO<A>, A> interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
            return recurse(ioZ.interpret(Psi.psi(f)));
        }
    }

    private static final class Psi<Z, A> implements Interpreter<Z, IO<A>> {
        private final Fn1<? super Z, ? extends IO<A>> f;

        private Psi(Fn1<? super Z, ? extends IO<A>> f) {
            this.f = f;
        }

        @Override
        public IO<A> interpret(Z z) {
            return f.apply(z);
        }

        @Override
        public IO<A> interpret(Fn0<Z> thunk) {
            return f.apply(thunk.apply());
        }

        @Override
        public IO<A> interpret(Callback<? super Callback<? super Z>> k) {
            return f.apply(await(k));
        }

        @Override
        public <Y> IO<A> interpret(IO<Y> ioY, IO<Fn1<? super Y, ? extends Z>> ioG) {
            return ioY.bind(y -> ioG.bind(g -> f.apply(g.apply(y))));
        }

        @Override
        public <Y> IO<A> interpret(IO<Y> ioY, Fn1<? super Y, ? extends IO<Z>> g) {
            return ioY.bind(y -> g.apply(y).bind(f));
        }

        private static <Z, A> Psi<Z, A> psi(Fn1<? super Z, ? extends IO<A>> f) {
            return new Psi<>(f);
        }
    }

    @SuppressWarnings("unchecked")
    public static <A> UnsafeRunSync<A> unsafeRunSync() {
        return (UnsafeRunSync<A>) INSTANCE;
    }
}
