package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.newIO.UnsafeRunSync.unsafeRunSync;

public abstract class IO<A> {

    private IO() {
    }

    public abstract <R> R interpret(Interpreter<A, R> interpreter);

    public final <B> IO<B> then(Fn1<? super A, ? extends IO<B>> f) {
        return new Sequential<>(this, f);
    }

    public final <B> IO<B> fork(IO<Fn1<? super A, ? extends B>> ioF) {
        return new Parallel<>(this, ioF);
    }

    public static <A> IO<A> io(A a) {
        return new Value<>(a);
    }

    public static <A> IO<A> io(Fn0<A> thunk) {
        return new Suspension<>(thunk);
    }

    public static IO<Unit> io(SideEffect sideEffect) {
        return io(() -> {
            sideEffect.Ω();
            return UNIT;
        });
    }

    public final A unsafePerformIO() {
        return interpret(unsafeRunSync());
    }

    private static final class Value<A> extends IO<A> {
        private final A a;

        private Value(A a) {
            this.a = a;
        }

        @Override
        public <R> R interpret(Interpreter<A, R> interpreter) {
            return interpreter.run(a);
        }
    }

    private static final class Suspension<A> extends IO<A> {
        private final Fn0<A> thunk;

        private Suspension(Fn0<A> thunk) {
            this.thunk = thunk;
        }

        @Override
        public <R> R interpret(Interpreter<A, R> interpreter) {
            return interpreter.run(thunk);
        }
    }

    private static final class Parallel<Z, A> extends IO<A> {
        private final IO<Z>                           ioZ;
        private final IO<Fn1<? super Z, ? extends A>> ioF;

        private Parallel(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
            this.ioZ = ioZ;
            this.ioF = ioF;
        }

        @Override
        public <R> R interpret(Interpreter<A, R> interpreter) {
            return interpreter.run(ioZ, ioF);
        }
    }

    private static final class Sequential<Z, A> extends IO<A> {
        private final IO<Z>                           ioZ;
        private final Fn1<? super Z, ? extends IO<A>> f;

        private Sequential(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
            this.ioZ = ioZ;
            this.f   = f;
        }

        @Override
        public <R> R interpret(Interpreter<A, R> interpreter) {
            return interpreter.run(ioZ, f);
        }
    }
}
