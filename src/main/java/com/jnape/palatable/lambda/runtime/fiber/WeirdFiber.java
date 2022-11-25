package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.runtime.fiber.Result.Successful;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;

public interface WeirdFiber<A> {

    void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback);

    static <B> WeirdFiber<B> forever(WeirdFiber<?> f) {
        return f.bind(__ -> forever(f));
    }

    static <A> WeirdFiber<A> fiber(BiConsumer<? super Canceller, ? super Consumer<? super Result<A>>> k) {
        return (s, c, k_) -> k.accept(c, k_);
    }

    static WeirdFiber<Unit> fiber(Runnable r) {
        return take2(() -> {
            r.run();
            return UNIT;
        });
    }

    static <A> WeirdFiber<A> result(Consumer<? super Consumer<? super Result<A>>> k) {
        return (s, c, k2) -> k.accept(k2);
    }

    static <A> WeirdFiber<A> take2(Fn0<? extends A> fn) {
        return (s, c, k) -> k.accept(Result.successful(fn.apply()));
    }

    static WeirdFiber<Unit> successful() {
        return successful(UNIT);
    }

    static <A> WeirdFiber<A> successful(A a) {
        return (s, c, k) -> k.accept(Result.successful(a));
    }

    static <A> WeirdFiber<A> failed(Throwable reason) {
        return (s, c, k) -> k.accept(Result.failed(reason));
    }

//    static Fiber<Unit> park(Duration duration) {
//        return (s, c, k) -> s.schedule(duration, () -> k.accept(Result.successful(UNIT)));
//    }

    @SuppressWarnings("unchecked")
    static <A> WeirdFiber<A> cancelled() {
        return (WeirdFiber<A>) Cancelled.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    static <A> WeirdFiber<A> never() {
        return (WeirdFiber<A>) Never.INSTANCE;
    }

    static <A> WeirdFiber<A> withCancellation(WeirdFiber<A> fiber) {
        return (scheduler, canceller, callback) -> {
            if (canceller.cancelled())
                callback.accept(Result.cancelled());
            else
                fiber.execute(scheduler, canceller, callback);
        };
    }

    default <B> WeirdFiber<B> bind(Fn1<? super A, ? extends WeirdFiber<B>> f) {
        return new Bind<>(this, f);
    }
}

final class Never<A> implements WeirdFiber<A> {
    static final Never<?> INSTANCE = new Never<>();

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
    }
}

final class Cancelled<A> implements WeirdFiber<A> {
    static final Cancelled<?> INSTANCE = new Cancelled<>();

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        callback.accept(Result.cancelled());
    }
}

record Bind<Z, A>(WeirdFiber<Z> fiberZ, Fn1<? super Z, ? extends WeirdFiber<A>> f) implements WeirdFiber<A> {

    interface Eliminator<A> {
        <Z> void apply(WeirdFiber<Z> fiberZ, Fn1<? super Z, ? extends WeirdFiber<A>> f);
    }

    void eliminate(Eliminator<A> eliminator) {
        eliminator.apply(fiberZ, f);
    }

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        tick(this, scheduler, callback, canceller, 1);
    }

    private static final int stackFrameTransplantDepth = 512;

    public static <Z, A> void tick(Bind<Z, A> bind, Scheduler scheduler, Consumer<? super Result<A>> ultimateCallback,
                                   Canceller canceller, int stackDepth) {
        if (canceller.cancelled()) {
            ultimateCallback.accept(Result.cancelled());
            return;
        }
        if (bind.fiberZ() instanceof Bind<?, Z> bindZ) {
            bindZ.eliminate(new Eliminator<>() {
                @Override
                public <Y> void apply(WeirdFiber<Y> fiberY, Fn1<? super Y, ? extends WeirdFiber<Z>> g) {
                    if (stackDepth == stackFrameTransplantDepth)
                        scheduler.schedule(() -> new Bind<>(fiberY, y -> new Bind<>(g.apply(y), bind.f()))
                                .execute(scheduler, canceller, ultimateCallback));
                    else {
                        tick(new Bind<>(fiberY, y -> new Bind<>(g.apply(y), bind.f())), scheduler, ultimateCallback, canceller, stackDepth + 1);
                    }
                }
            });
        } else {
            bind.fiberZ().execute(scheduler, canceller, resultZ -> {
                if (resultZ instanceof Result.Cancelled<Z> cancelledZ) {
                    ultimateCallback.accept(cancelledZ.contort());
                } else if (resultZ instanceof Successful<Z> successZ) {
                    WeirdFiber<A> fiberA = bind.f().apply(successZ.value());
                    if (fiberA instanceof Bind<?, A> bindA) {
                        if (stackDepth == stackFrameTransplantDepth)
                            scheduler.schedule(() -> bindA.execute(scheduler, canceller, ultimateCallback));
                        else {
                            tick(bindA, scheduler, ultimateCallback, canceller, stackDepth + 1);
                        }
                    } else {
                        if (stackDepth == stackFrameTransplantDepth)
                            scheduler.schedule(() -> fiberA.execute(scheduler, canceller, ultimateCallback));
                        else
                            fiberA.execute(scheduler, canceller, ultimateCallback);
                    }
                } else {
                    ultimateCallback.accept(((Result.Failed<Z>) resultZ).contort());
                }
            });
        }
    }
}