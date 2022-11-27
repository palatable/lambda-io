package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.runtime.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;

//todo: should forever be its own Record?
public sealed interface Fiber<A> {

    void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback);

    default <B> Fiber<B> bind(Function<? super A, ? extends Fiber<B>> fn) {
        return new Bind<>(this, fn);
    }

    static <A> Fiber<A> result(Result<A> result) {
        return new Value<>(result);
    }

    static <A> Fiber<A> succeeded(A a) {
        return result(success(a));
    }

    static Fiber<Unit> succeeded() {
        return Value.SUCCESS_UNIT;
    }

    static <A> Fiber<A> failed(Throwable t) {
        return result(failure(t));
    }

    static <A> Fiber<A> cancelled() {
        return Value.cancelled();
    }

    static <A> Fiber<A> never() {
        return Never.instance();
    }

    static <A> Fiber<A> fiber(Supplier<? extends A> task) {
        return new Suspension<>(task);
    }

    static Fiber<Unit> fiber(Runnable action) {
        return fiber(() -> {
            action.run();
            return UNIT;
        });
    }
}

record Value<A>(Result<A> result) implements Fiber<A> {
    static final Value<Unit> SUCCESS_UNIT = new Value<>(success());
    static final Value<?>    CANCELLED    = new Value<>(cancellation());

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        callback.accept(result);
    }

    @SuppressWarnings("unchecked")
    static <A> Value<A> cancelled() {
        return (Value<A>) CANCELLED;
    }
}

record Suspension<A>(Supplier<? extends A> task) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        if (!canceller.cancelled())
            scheduler.schedule(() -> {
                Result<A> result;
                if (canceller.cancelled())
                    result = cancellation();
                else
                    try {
                        result = success(task.get());
                    } catch (Throwable t) {
                        result = failure(t);
                    }
                callback.accept(result);
            });
        else
            callback.accept(cancellation());
    }
}

record Bind<Z, A>(Fiber<Z> fiberZ, Function<? super Z, ? extends Fiber<A>> f) implements Fiber<A> {

    private static final int maxStackDepth = 512;

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        tick0(this, scheduler, canceller, callback, 1);
    }

    private <R> R eliminate(Eliminator<R, A> eliminator) {
        return eliminator.eliminate(fiberZ, f);
    }

    private static <A> void tick(Bind<?, A> bind, Scheduler scheduler, Canceller canceller,
                                 Consumer<? super Result<A>> callback, int stackDepth) {
        if (bind.fiberZ instanceof Bind<?, ?>)
            if (canceller.cancelled())
                callback.accept(cancellation());
            else
                scheduler.schedule(() -> tick0(rightAssociated(bind), scheduler, canceller, callback, 0));
        else
            tick0(bind, scheduler, canceller, callback, stackDepth);
    }

    private static <X, A> void tick0(Bind<X, A> bind, Scheduler scheduler, Canceller canceller,
                                     Consumer<? super Result<A>> finalCallback, int stackDepth) {
        if (canceller.cancelled()) {
            finalCallback.accept(cancellation());
        } else {
            if (stackDepth == maxStackDepth)
                scheduler.schedule(() -> bind.fiberZ.execute(scheduler, canceller, resX -> {
                    if (resX instanceof Result.Success<X> success) {
                        Fiber<A> nextFiber = bind.f.apply(success.value());
                        if (nextFiber instanceof Bind<?, A> nextBind) {
                            // tick
                            tick0(nextBind, scheduler, canceller, finalCallback, 1);
                        } else nextFiber.execute(scheduler, canceller, finalCallback);
                    } else if (resX instanceof Result.Failure<X> failure) {
                        finalCallback.accept(failure.contort());
                    } else {
                        finalCallback.accept(cancellation());
                    }
                }));
            else
                bind.fiberZ.execute(scheduler, canceller, resX -> {
                    if (resX instanceof Result.Success<X> success) {
                        Fiber<A> nextFiber = bind.f.apply(success.value());
                        if (nextFiber instanceof Bind<?, A> nextBind) {
                            // tick
                            tick0(nextBind, scheduler, canceller, finalCallback, stackDepth + 1);
                        } else nextFiber.execute(scheduler, canceller, finalCallback);
                    } else if (resX instanceof Result.Failure<X> failure) {
                        finalCallback.accept(failure.contort());
                    } else {
                        finalCallback.accept(cancellation());
                    }
                });
        }
    }

    private static <A> Bind<?, A> rightAssociated(Bind<?, A> bind) {
        Bind<?, A> rightAssociated = bind;
        while (rightAssociated.fiberZ instanceof Bind<?, ?>) {
            rightAssociated = rightAssociated.eliminate(new Eliminator<>() {
                @Override
                public <Z> Bind<?, A> eliminate(Fiber<Z> fiber, Function<? super Z, ? extends Fiber<A>> f) {
                    return ((Bind<?, Z>) fiber).eliminate(new Eliminator<>() {
                        @Override
                        public <Y> Bind<?, A> eliminate(Fiber<Y> fiberY, Function<? super Y, ? extends Fiber<Z>> g) {
                            return new Bind<>(fiberY, y -> new Bind<>(g.apply(y), f));
                        }
                    });
                }
            });
        }
        return rightAssociated;
    }

    interface Eliminator<R, A> {
        <Z> R eliminate(Fiber<Z> fiber, Function<? super Z, ? extends Fiber<A>> fn);
    }
}

final class Never<A> implements Fiber<A> {
    static final Never<?> INSTANCE = new Never<>();

    private Never() {
    }

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
    }

    @SuppressWarnings("unchecked")
    static <A> Never<A> instance() {
        return (Never<A>) INSTANCE;
    }
}