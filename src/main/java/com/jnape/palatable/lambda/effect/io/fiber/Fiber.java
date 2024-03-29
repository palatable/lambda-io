package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.effect.io.fiber.Result.Success;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static com.jnape.palatable.lambda.internal.Runtime.throwChecked;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;

//todo: onCancel(Runnable) & catch(e -> Fiber<B>)
public sealed interface Fiber<A> {

    default void unsafeRunAsync(Consumer<? super Result<A>> callback) {
        Runtime.JVM.get().schedule(this, callback);
    }

    default A unsafeRunSync() {
        Result<A> result = new CompletableFuture<Result<A>>() {{
            unsafeRunAsync(this::complete);
        }}.join();
        if (result instanceof Success<A> success)
            return success.value();
        else if (result instanceof Result.Failure<A> failure)
            throw throwChecked(failure.reason());
        else
            throw throwChecked(new CancellationException());
    }

    default <B> Fiber<B> bind(Function<? super A, ? extends Fiber<B>> fn) {
        return new Bind<>(this, fn);
    }

    static <A> Fiber<A> fiber(Consumer<? super Consumer<? super Result<A>>> k) {
        return new Suspension<>(k);
    }

    static <A> Fiber<A> fiber(Supplier<? extends A> task) {
        return fiber(k -> {
            Result<A> result;
            try {
                result = success(task.get());
            } catch (Throwable t) {
                result = failure(t);
            }
            k.accept(result);
        });
    }

    static <A> Fiber<A> result(Result<A> result) {
        if (result == Result.success()) {
            @SuppressWarnings("unchecked")
            Fiber<A> succeededUnit = (Fiber<A>) succeeded();
            return succeededUnit;
        }
        return new Value<>(result);
    }

    static <A> Fiber<A> succeeded(A a) {
        return result(success(a));
    }

    static Fiber<Unit> succeeded() {
        return Value.SUCCESS_UNIT;
    }

    static <A> Fiber<A> failed(Throwable cause) {
        return result(failure(cause));
    }

    @SafeVarargs
    static <A> Fiber<A> race(Fiber<A> fiberA, Fiber<A> fiberB, Fiber<A>... rest) {
        return new Race<>(new ArrayList<>(rest.length + 2) {{
            add(fiberA);
            add(fiberB);
            addAll(asList(rest));
        }});
    }

    @SafeVarargs
    static <A> Fiber<List<A>> parallel(Fiber<A>... fibers) {
        return parallel(asList(fibers));
    }

    static <A> Fiber<List<A>> parallel(List<Fiber<A>> fibers) {
        return new Parallel<>(fibers, identity());
    }

    //todo: is this sensible to expose?
    static <A> Fiber<A> cancelled() {
        return Value.cancelled();
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

    //todo: Duration#toNanos will explode when overflowing, do something better
    static <A> Fiber<A> delay(Fiber<A> fiber, Duration duration) {
        return new Delay<>(fiber, duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    static <A> Fiber<A> pin(Fiber<A> fiber, Executor executor) {
        return new Pin<>(fiber, executor);
    }
}

record Suspension<A>(Consumer<? super Consumer<? super Result<A>>> k) implements Fiber<A> {
}

record Value<A>(Result<A> result) implements Fiber<A> {
    static final Value<Unit> SUCCESS_UNIT = new Value<>(success());
    static final Value<?>    CANCELLED    = new Value<>(cancellation());

    @SuppressWarnings("unchecked")
    static <A> Value<A> cancelled() {
        return (Value<A>) CANCELLED;
    }
}

record Pin<A>(Fiber<A> fiber, Executor executor) implements Fiber<A> {
}

record Forever<Z, A>(Fiber<Z> fiber) implements Fiber<A> {
}

record Delay<A>(Fiber<A> fiber, long delay, TimeUnit timeUnit) implements Fiber<A> {
}

record Bind<Z, A>(Fiber<Z> fiberZ, Function<? super Z, ? extends Fiber<A>> f) implements Fiber<A> {

    private <R> R eliminate(Eliminator<A, R> eliminator) {
        return eliminator.eliminate(fiberZ, f);
    }

    //todo: compare with efficiency of old lambda IO approach of an untyped list storing arrows
    public Bind<?, A> rightAssociated() {
        Bind<?, A> rightAssociated = this;
        Bind.Eliminator<A, Bind<?, A>> eliminator = new Bind.Eliminator<>() {
            @Override
            public <Y> Bind<?, A> eliminate(Fiber<Y> fiber, Function<? super Y, ? extends Fiber<A>> f) {
                return ((Bind<?, Y>) fiber).eliminate(new Bind.Eliminator<>() {
                    @Override
                    public <X> Bind<?, A> eliminate(Fiber<X> fiberX, Function<? super X, ? extends Fiber<Y>> g) {
                        return new Bind<>(fiberX, y -> new Bind<>(g.apply(y), f));
                    }
                });
            }
        };

        while (rightAssociated.fiberZ() instanceof Bind<?, ?>) {
            rightAssociated = rightAssociated.eliminate(eliminator);
        }

        return rightAssociated;
    }

    private interface Eliminator<A, R> {
        <Z> R eliminate(Fiber<Z> fiberZ, Function<? super Z, ? extends Fiber<A>> f);
    }
}

record Race<A>(List<Fiber<A>> fibers) implements Fiber<A> {
}

record Parallel<Z, A>(List<Fiber<Z>> fibers, Function<? super List<Z>, ? extends A> f) implements Fiber<A> {
}