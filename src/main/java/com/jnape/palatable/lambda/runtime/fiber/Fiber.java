package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.runtime.fiber.internal.Array;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.runtime.fiber.Successful.SUCCESS_UNIT;

public sealed interface Fiber<A> {

    void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback);

    static <B> Fiber<B> forever(Fiber<?> f) {
        return f.bind(__ -> forever(f));
    }

    static <A> Fiber<A> fiber(BiConsumer<? super Canceller, ? super Consumer<? super Result<A>>> k) {
        return new Continuation<>(k);
    }

    static Fiber<Unit> fiber(Runnable r) {
        return fiber((__, k) -> {
            r.run();
            k.accept(Result.successful(UNIT));
        });
    }

    static Fiber<Unit> successful() {
        return SUCCESS_UNIT;
    }

    static <A> Fiber<A> successful(A a) {
        return new Successful<>(a);
    }

    static <A> Fiber<A> failed(Throwable reason) {
        return new Failed<>(reason);
    }

    static Fiber<Unit> park(Duration duration) {
        return new Park(duration);
    }

    @SuppressWarnings("unchecked")
    static <A> Fiber<A> cancelled() {
        return (Fiber<A>) Cancelled.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    static <A> Fiber<A> never() {
        return (Fiber<A>) Never.INSTANCE;
    }

    static <A> Fiber<A> withCancellation(Fiber<A> fiber) {
        return new WithCancellation<>(fiber);
    }

    default <B> Fiber<B> bind(Fn1<? super A, ? extends Fiber<B>> f) {
        return new Bind<>(this, f);
    }

    @SafeVarargs
    static <A> Fiber<Array<A>> parallel(Fiber<A>... fibers) {
        return fibers.length == 0 ? Parallel.empty() : new Parallel<A>(fibers);
    }

    static <A> Fiber<A> fork(Fiber<A> fiber) {
        return new Fork<>(fiber);
    }

}

record Forever<A>(Fiber<?> fiber) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        scheduler.schedule(() -> fiber.execute(scheduler, canceller, res -> {
            if (res.isCancelled())
                callback.accept(Result.cancelled());
            else if (res instanceof Failure<?> failure) {
                callback.accept(failure.contort());
            } else {
                execute(scheduler, canceller, callback);
            }
        }));
    }
}

final class Never<A> implements Fiber<A> {
    static final Never<?> INSTANCE = new Never<>();

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
    }
}

final class Cancelled<A> implements Fiber<A> {
    static final Cancelled<?> INSTANCE = new Cancelled<>();

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        callback.accept(Result.cancelled());
    }
}

record Failed<A>(Throwable reason) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        callback.accept(Result.failed(reason));
    }
}

record Successful<A>(A a) implements Fiber<A> {
    static final Successful<Unit> SUCCESS_UNIT = new Successful<>(UNIT);

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        callback.accept(Result.successful(a));
    }
}

record Park(Duration duration) implements Fiber<Unit> {

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<Unit>> callback) {
        scheduler.schedule(duration, () -> callback.accept(Result.successful(UNIT)), canceller);
    }
}

record WithCancellation<A>(Fiber<A> fiber) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        if (canceller.cancelled())
            callback.accept(Result.cancelled());
        else
            fiber.execute(scheduler, canceller, callback);
    }
}

record Bind<Z, A>(Fiber<Z> fiberZ, Fn1<? super Z, ? extends Fiber<A>> f) implements Fiber<A> {

    interface Eliminator<A> {
        <Z> void apply(Fiber<Z> fiberZ, Fn1<? super Z, ? extends Fiber<A>> f);
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
                public <Y> void apply(Fiber<Y> fiberY, Fn1<? super Y, ? extends Fiber<Z>> g) {
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
                } else if (resultZ instanceof Success<Z> successZ) {
                    Fiber<A> fiberA = bind.f().apply(successZ.value());
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
                    ultimateCallback.accept(((Failure<Z>) resultZ).contort());
                }
            });
        }
    }
}

record Fork<A>(Fiber<A> fiber) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        scheduler.schedule(() -> fiber.execute(scheduler, canceller, callback));
    }
}

record Continuation<A>(BiConsumer<? super Canceller, ? super Consumer<? super Result<A>>> k) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        k.accept(canceller, callback);
    }
}

record Parallel<A>(Fiber<A>[] fibers) implements Fiber<Array<A>> {

    private static final Fiber<?> EMPTY = Fiber.successful(Array.empty());

    @SuppressWarnings("unchecked")
    public static <A> Fiber<Array<A>> empty() {
        return (Fiber<Array<A>>) EMPTY;
    }

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<Array<A>>> callback) {
        Canceller     shared  = canceller.addChild();
        Object[]      results = new Object[fibers.length];
        AtomicInteger counter = new AtomicInteger(fibers.length);
        Consumer<Integer> schedule = i -> scheduler.schedule(() -> {
            if (!shared.cancelled())
                fibers[i].execute(scheduler, shared, res -> {
                    if (res.isCancelled()) {
                        if (counter.getAndSet(-1) != -1) {
                            callback.accept(Result.cancelled());
                        }
                    } else if (res instanceof Failure<?> failure) {
                        if (counter.getAndSet(-1) != -1) {
                            shared.cancel();
                            callback.accept(failure.contort());
                        }
                    } else {
                        results[i] = ((Success<A>) res).value();
                        if (counter.getAndDecrement() == 1) {
                            @SuppressWarnings("unchecked")
                            Array<A> array = (Array<A>) Array.shallowCopy(results);
                            callback.accept(Result.successful(array));
                        }
                    }
                });
            else if (counter.getAndSet(-1) != -1)
                callback.accept(Result.cancelled());
        });

        for (int i = 0; i < fibers.length; i++)
             schedule.accept(i);
    }
}