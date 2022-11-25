package research.lambda.effect.io;

import com.jnape.palatable.lambda.adt.Unit;
import research.lambda.effect.io.fiber2.old.FiberResult;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static research.lambda.effect.io.fiber2.old.FiberResult.failure;
import static research.lambda.effect.io.fiber2.old.FiberResult.success;

public sealed interface IO<A> {

    <R> R interpret(Interpreter<A, R> interpreter);

    default A unsafePerformIO() {
        return IOPlatform.system().unsafeRun(this);
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
        return io(k -> {
            A         a      = null;
            Throwable thrown = null;
            try {a = thunk.apply();} catch (Throwable t) {thrown = t;}
            k.accept(thrown != null ? failure(thrown) : success(a));
        });
    }

    static IO<Unit> io(SideEffect sideEffect) {
        return io(() -> {
            sideEffect.Ω();
            return UNIT;
        });
    }

    static <A> IO<A> io(Consumer<? super Consumer<? super FiberResult<A>>> k) {
        return new Suspension<>(k);
    }

    static <A> IO<A> fork(Fn0<? extends A> thunk, Executor executor) {
        return io(k -> executor.execute(() -> k.accept(success(thunk.apply()))));
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

record Suspension<A>(Consumer<? super Consumer<? super FiberResult<A>>> k) implements IO<A> {
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
