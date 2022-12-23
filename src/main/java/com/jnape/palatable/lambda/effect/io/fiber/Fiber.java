package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;

//todo: ThreadLocal choice for current trampoline / scheduler ("shift" model)?
//todo: Fiber#fork(Fiber, Scheduler)
public sealed interface Fiber<A> {

    default <B> Fiber<B> bind(Function<? super A, ? extends Fiber<B>> fn) {
        return new Bind<>(this, fn);
    }

    static <A> Fiber<A> fiber(Consumer<? super Consumer<? super Result<A>>> k) {
        return new Suspension<>(k);
    }

    //todo: env variable to determine whether this is treated as blocking or non-blocking by default for upgrade path?
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

    static <A> Fiber<A> race(Fiber<A> fiberA, Fiber<A> fiberB) {
        return new Race<>(fiberA, fiberB);
    }

    //todo: is this sensible to expose?
    static <A> Fiber<A> cancelled() {
        return Value.cancelled();
    }

    //todo: is this sensible to expose?
    static <A> Fiber<A> never() {
        return Never.instance();
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
}

record Value<A>(Result<A> result) implements Fiber<A> {
    static final Value<Unit> SUCCESS_UNIT = new Value<>(success());
    static final Value<?>    CANCELLED    = new Value<>(cancellation());

    @SuppressWarnings("unchecked")
    static <A> Value<A> cancelled() {
        return (Value<A>) CANCELLED;
    }
}

record Forever<A>(Fiber<?> fiber) implements Fiber<A> {
}

record Bind<Z, A>(Fiber<Z> fiberZ, Function<? super Z, ? extends Fiber<A>> f) implements Fiber<A> {

    <R> R eliminate(Eliminator<A, R> eliminator) {
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

        //todo: scan two nested binds ahead before re-association for specialized #forever() case?
        while (rightAssociated.fiberZ() instanceof Bind<?, ?>) {
            rightAssociated = rightAssociated.eliminate(eliminator);
        }

        return rightAssociated;
    }

    interface Eliminator<A, R> {
        <Z> R eliminate(Fiber<Z> fiberZ, Function<? super Z, ? extends Fiber<A>> f);
    }
}

record Race<A>(Fiber<A> fiberA, Fiber<A> fiberB) implements Fiber<A> {
}

final class Never<A> implements Fiber<A> {
    static final Never<?> INSTANCE = new Never<>();

    private Never() {
    }

    @SuppressWarnings("unchecked")
    static <A> Never<A> instance() {
        return (Never<A>) INSTANCE;
    }
}