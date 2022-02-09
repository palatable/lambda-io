package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.runtime.fiber.internal.Array;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Constantly.constantly;
import static com.jnape.palatable.lambda.runtime.fiber.Successful.SUCCESS_UNIT;

public interface Fiber<A> {

    static <A, B> Fiber<B> forever(Fiber<A> f) {
        return f.bind(__ -> forever(f));
    }

    void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback);

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
        return withCancellation((scheduler, canceller, callback) -> {
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
        });
    }

    public static void main(String[] args) {
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        parallel(Fiber.park(Duration.ofSeconds(1)).bind(constantly(successful(2))),
                 Fiber.park(Duration.ofSeconds(1)).bind(constantly(successful(2))),
                 Fiber.park(Duration.ofSeconds(1)).bind(constantly(successful(2))),
                 Fiber.park(Duration.ofSeconds(1)).bind(constantly(successful(2))),
                 Fiber.park(Duration.ofSeconds(1)).bind(constantly(successful(2))),
                 Fiber.park(Duration.ofSeconds(1)).bind(constantly(successful(2))),
                 Fiber.park(Duration.ofSeconds(1)).bind(constantly(successful(2))),
                 Fiber.park(Duration.ofSeconds(1)).bind(constantly(successful(2))),
                 parallel(successful(1), failed(new IllegalStateException()), never()).bind(constantly(successful(2))),
                 Fiber.successful(1))
                .execute(Scheduler.scheduledExecutorService(ex), Canceller.root(), r -> {
                    System.out.println(r);
                    ex.shutdown();
                });
    }

    static <A> Fiber<A> fork(Fiber<A> fiber) {
        return new Fork<>(fiber);
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

    interface Eliminator<A, R> {
        <Z> R apply(Fiber<Z> fiberZ, Fn1<? super Z, ? extends Fiber<A>> f);
    }

    interface EliminatorV<A> {
        <Z> void apply(Fiber<Z> fiberZ, Fn1<? super Z, ? extends Fiber<A>> f);
    }

    <R> R eliminate(Eliminator<A, R> eliminator) {
        return eliminator.apply(fiberZ, f);
    }

    void eliminate(EliminatorV<A> eliminator) {
        eliminator.apply(fiberZ, f);
    }

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        associateRight(this).eliminate(new EliminatorV<>() {
            @Override
            public <Y> void apply(Fiber<Y> fiberY, Fn1<? super Y, ? extends Fiber<A>> f) {
                fiberY.execute(scheduler, canceller, resZ -> {
                    if (resZ.isCancelled()) {
                        callback.accept(Result.cancelled());
                    } else if (resZ instanceof Result.Failed<?> failedZ) {
                        callback.accept(failedZ.contort());
                    } else {
                        Fiber.fork(f.apply(((Result.Successful<Y>) resZ).value()))
                                .execute(scheduler, canceller, callback);
                    }
                });
            }
        });
    }

    private static <A> Bind<?, A> associateRight(Bind<?, A> bind) {
        while (bind.fiberZ() instanceof Bind<?, ?>) {
            bind = bind.eliminate(new Eliminator<>() {
                @Override
                public <Z> Bind<?, A> apply(Fiber<Z> fiberZ, Fn1<? super Z, ? extends Fiber<A>> f) {
                    return ((Bind<?, Z>) fiberZ).eliminate(new Eliminator<>() {
                        @Override
                        public <Y> Bind<Y, A> apply(Fiber<Y> fiberY, Fn1<? super Y, ? extends Fiber<Z>> g) {
                            return new Bind<>(fiberY, y -> new Bind<>(g.apply(y), f));
                        }
                    });
                }
            });
        }
        return bind;
    }
}

record Fork<A>(Fiber<A> fiber) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        scheduler.schedule(() -> fiber.execute(scheduler, canceller, callback));
    }
}