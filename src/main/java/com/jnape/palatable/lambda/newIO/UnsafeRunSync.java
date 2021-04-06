package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;

public final class UnsafeRunSync<A> implements Interpreter<A, A> {

    private static final UnsafeRunSync<?> INSTANCE = new UnsafeRunSync<>();

    private UnsafeRunSync() {
    }

    @Override
    public A run(A a) {
        return a;
    }

    @Override
    public A run(Fn0<A> thunk) {
        return thunk.apply();
    }

    @Override
    public <Z> A run(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
        Z                           z = unsafeRun(ioZ);
        Fn1<? super Z, ? extends A> f = unsafeRun(ioF);
        return f.apply(z);
    }

    @Override
    public <Z> A run(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
        return unsafeRun(f.apply(unsafeRun(ioZ)));
    }

    private static <A> A unsafeRun(IO<A> io) {
        Interpreter<A, RecursiveResult<IO<A>, A>> phi = Phi.phi();
        return trampoline(io_ -> io_.interpret(phi), io);
    }

    private static final class Phi<A> implements Interpreter<A, RecursiveResult<IO<A>, A>> {

        private static final Phi<?> INSTANCE = new Phi<>();

        @SuppressWarnings({"unchecked"})
        public static <A> Phi<A> phi() {
            return (Phi<A>) INSTANCE;
        }

        @Override
        public RecursiveResult<IO<A>, A> run(A a) {
            return terminate(a);
        }

        @Override
        public RecursiveResult<IO<A>, A> run(Fn0<A> thunk) {
            return terminate(thunk.apply());
        }

        @Override
        public <Z> RecursiveResult<IO<A>, A> run(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
            return recurse(ioZ.then(z -> ioF.then(f -> IO.io(f.apply(z)))));
        }

        @Override
        public <Z> RecursiveResult<IO<A>, A> run(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
            return recurse(ioZ.interpret(Psi.psi(f)));
        }
    }

    private static final class Psi<Z, A> implements Interpreter<Z, IO<A>> {
        private final Fn1<? super Z, ? extends IO<A>> f;

        private Psi(Fn1<? super Z, ? extends IO<A>> f) {
            this.f = f;
        }

        @Override
        public IO<A> run(Z z) {
            return f.apply(z);
        }

        @Override
        public IO<A> run(Fn0<Z> thunk) {
            return f.apply(thunk.apply());
        }

        @Override
        public <Y> IO<A> run(IO<Y> ioY, IO<Fn1<? super Y, ? extends Z>> ioG) {
            return ioY.then(y -> ioG.then(g -> f.apply(g.apply(y))));
        }

        @Override
        public <Y> IO<A> run(IO<Y> ioY, Fn1<? super Y, ? extends IO<Z>> g) {
            return ioY.then(y -> g.apply(y).then(f));
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
