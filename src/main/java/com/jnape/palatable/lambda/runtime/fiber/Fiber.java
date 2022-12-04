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
//todo: should Suspensions:
// - try/catch
// - shift onto scheduler
//todo: should fiber execution be wrapped in try/catch and re-throw as critical error?
//todo: ThreadLocal choice for current trampoline / scheduler ("shift" model)?
public sealed interface Fiber<A> {

    void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback);

    default <B> Fiber<B> bind(Function<? super A, ? extends Fiber<B>> fn) {
        return new Bind<>(this, fn);
    }

    static <A> Fiber<A> fiber(Consumer<? super Consumer<? super Result<A>>> k) {
        return new Suspension<>(k);
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

    // TODO: should this even be convenient? Should it invoke Canceller#cancel?
    static <A> Fiber<A> cancelled() {
        return Value.cancelled();
    }

    static <A> Fiber<A> never() {
        return Never.instance();
    }

    static <A> Fiber<A> fiber(Supplier<? extends A> task) {
        return new Suspension<>(k -> {
            Result<A> result;
            try {
                result = success(task.get());
            } catch (Throwable t) {
                result = failure(t);
            }
            k.accept(result);
        });
    }

    static Fiber<Unit> fiber(Runnable action) {
        return fiber(() -> {
            action.run();
            return UNIT;
        });
    }

    static <A> Fiber<A> forever(Fiber<?> fiber) {
        return new Forever<>(fiber);
    }

}

record Suspension<A>(Consumer<? super Consumer<? super Result<A>>> k) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        if (canceller.cancelled())
            callback.accept(cancellation());
        else
            //todo: should this implicitly run on scheduler or expect scheduler choice to happen at execution time?
            // - eliminating this "schedule" call unsurprisingly dramatically improves performance
            scheduler.schedule(() -> k.accept(callback));
    }
}

class Constants {
    static final int maxFiberRecursionDepth = 512;
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

record Forever<A>(Fiber<?> fiber) implements Fiber<A> {

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        scheduler.schedule(() -> loop(scheduler, canceller, callback, 1));
    }

    private void loop(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback, int stackDepth) {
        if (stackDepth == Constants.maxFiberRecursionDepth - 1) {
            execute(scheduler, canceller, callback);
            return;
        }

        if (canceller.cancelled())
            callback.accept(cancellation());
        else {
            fiber.execute(scheduler, canceller, res -> {
                if (res instanceof Result.Success<?>)
                    loop(scheduler, canceller, callback, stackDepth + 1);
                else if (res instanceof Result.Failure<?> f)
                    callback.accept(f.contort());
                else
                    callback.accept(cancellation());
            });
        }
    }
}

record Bind<Z, A>(Fiber<Z> fiberZ, Function<? super Z, ? extends Fiber<A>> f) implements Fiber<A> {

    @Override
    public void execute(Scheduler scheduler, Canceller canceller, Consumer<? super Result<A>> callback) {
        tick(this, scheduler, canceller, callback, 1);
    }

    private <R> R eliminate(Eliminator<R, A> eliminator) {
        return eliminator.eliminate(fiberZ, f);
    }

    private static <A> void tick(Bind<?, A> bind, Scheduler scheduler, Canceller canceller,
                                 Consumer<? super Result<A>> callback, int tickRecursionDepth) {
        tick0((Bind<?, A>) (bind.fiberZ instanceof Bind<?, ?> ? rightAssociated(bind) : bind),
              scheduler, canceller, callback, tickRecursionDepth + 1);
    }

    private static <X, A> void tick0(Bind<X, A> bind, Scheduler scheduler, Canceller canceller,
                                     Consumer<? super Result<A>> finalCallback, int stackDepth) {
        if (canceller.cancelled()) {
            finalCallback.accept(cancellation());
        } else {
            if (stackDepth == Constants.maxFiberRecursionDepth)
                scheduler.schedule(() -> bind.fiberZ.execute(scheduler, canceller, resX -> {
                    if (resX instanceof Result.Success<X> success) {
                        Fiber<A> nextFiber = bind.f.apply(success.value());
                        if (nextFiber instanceof Bind<?, A> nextBind) {
                            tick(nextBind, scheduler, canceller, finalCallback, 1);
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
                            tick(nextBind, scheduler, canceller, finalCallback, stackDepth + 1);
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