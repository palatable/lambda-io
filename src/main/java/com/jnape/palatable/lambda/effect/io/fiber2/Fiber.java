package com.jnape.palatable.lambda.effect.io.fiber2;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.adt.choice.Choice2;
import com.jnape.palatable.lambda.effect.io.fiber.Cancel;
import com.jnape.palatable.lambda.effect.io.fiber.FiberCallback;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.Cancelled;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.Failure;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.Success;
import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.adt.choice.Choice2.a;
import static com.jnape.palatable.lambda.adt.choice.Choice2.b;
import static com.jnape.palatable.lambda.benchmark.Sample.sample;
import static com.jnape.palatable.lambda.effect.io.fiber.FiberResult.cancelled;
import static com.jnape.palatable.lambda.effect.io.fiber.FiberResult.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.FiberResult.success;
import static java.util.concurrent.TimeUnit.MICROSECONDS;

public interface Fiber<A> {
    void execute(Scheduler scheduler, Cancel cancel, FiberCallback<A> callback);

    default FiberResult<A> await(Scheduler scheduler, Cancel cancel) {
        return new CompletableFuture<FiberResult<A>>() {{
            execute(scheduler, cancel, this::complete);
        }}.join();
    }

    static <A> Fiber<A> fork(Fn0<? extends A> f) {
        return cancellable((scheduler, callback) -> scheduler.schedule(() -> {
            try {callback.call(success(f.apply()));} catch (Throwable t) {callback.call(failure(t));}
        }));
    }

    static <A> Fiber<A> fiber(Fn0<? extends A> f) {
        return cancellable((scheduler, callback) -> {
            try {callback.call(success(f.apply()));} catch (Throwable t) {callback.call(failure(t));}
        });
    }

    static Fiber<Unit> fork(SideEffect sideEffect) {
        return fork(() -> {
            sideEffect.Ω();
            return UNIT;
        });
    }

    static Fiber<Unit> fiber(SideEffect sideEffect) {
        return fiber(() -> {
            sideEffect.Ω();
            return UNIT;
        });
    }

    static <A, B> Fiber<Choice2<A, B>> race(Fiber<A> a, Fiber<B> b) {
        return (s, c, k) -> {
            if (c.cancelled())
                k.call(cancelled());
            else {
                AtomicBoolean flag  = new AtomicBoolean(false);
                Cancel        child = c.addChild();
                s.schedule(() -> a.execute(s, child, res -> {
                    if (!flag.getAndSet(true)) {
                        child.cancel();
                        k.call(c.cancelled() || res instanceof Cancelled<?>
                               ? cancelled()
                               : res instanceof Success<A> success
                                 ? success(a(success.result()))
                                 : ((Failure<?>) res).contort());
                    }
                }));
                s.schedule(() -> b.execute(s, child, res -> {
                    if (!flag.getAndSet(true)) {
                        child.cancel();
                        k.call(c.cancelled() || res instanceof Cancelled<?>
                               ? cancelled()
                               : res instanceof Success<B> success
                                 ? success(b(success.result()))
                                 : ((Failure<?>) res).contort());
                    }
                }));
            }
        };
    }


    static <A> Fiber<A> cancel() {
        return (scheduler, cancel, callback) -> {
            cancel.cancel();
            callback.call(cancelled());
        };
    }

    default <B> Fiber<B> bind(Fn1<? super A, ? extends Fiber<B>> f) {
        return new Bind<>(this, f);
    }

    public static <A, B> Fiber<B> forever(Fiber<A> fiber) {
        return fiber.bind(__ -> forever(fiber));
    }

    public static <A> Fiber<Unit> times(Fiber<?> fiber, long i) {
        return fiber.bind(__ -> i == 0 ? fork(UNIT) : times(fiber, i - 1));
    }

    public static <A> Fiber<A> never() {
        return (s, c, k) -> {};
    }

    static <A> Fiber<A> fork(A a) {
        return new Value<>(a);
    }

    static <A> Fiber<A> cancellable(BiConsumer<Scheduler, FiberCallback<A>> task) {
        return (scheduler, cancel, callback) -> {
            if (cancel.cancelled()) {
                callback.call(cancelled());
            } else
                task.accept(scheduler, callback);
        };
    }

    static <A> Fiber<A> cancellable(Consumer<FiberCallback<A>> task) {
        return cancellable((__, callback) -> task.accept(callback));
    }

    public static void main(String... args) {
        Fiber<Unit> fiber = forever(times(fiber(sample(10_000_000, MICROSECONDS)::mark), 100_000_000L)
                                            .bind(__ -> cancel()));

        race(fiber, never()).bind(__ -> fiber(Scheduler.shared()::shutdown))
                .execute(Scheduler.shared(), Cancel.root(), System.out::println);
    }
}

record Value<A>(A a) implements Fiber<A> {
    @Override
    public void execute(Scheduler scheduler, Cancel cancel, FiberCallback<A> callback) {
        callback.call(success(a));
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
    public void execute(Scheduler scheduler, Cancel cancel, FiberCallback<A> callback) {
        tick(this, scheduler, callback, cancel, 1);
    }

    private static final int stackFrameTransplantDepth = 512;

    private static <Z, A> void tick(Bind<Z, A> bind, Scheduler scheduler, FiberCallback<A> ultimateCallback,
                                    Cancel cancel, int stackDepth) {
        if (cancel.cancelled()) {
            ultimateCallback.call(cancelled());
            return;
        }
        if (bind.fiberZ() instanceof Bind<?, Z> bindZ) {
            bindZ.eliminate(new Eliminator<>() {
                @Override
                public <Y> void apply(Fiber<Y> fiberY, Fn1<? super Y, ? extends Fiber<Z>> g) {
                    if (stackDepth == stackFrameTransplantDepth)
                        scheduler.schedule(() -> new Bind<>(fiberY, y -> new Bind<>(g.apply(y), bind.f()))
                                .execute(scheduler, cancel, ultimateCallback));
                    else {
                        tick(new Bind<>(fiberY, y -> new Bind<>(g.apply(y), bind.f())), scheduler, ultimateCallback, cancel, stackDepth + 1);
                    }
                }
            });
        } else {
            bind.fiberZ().execute(scheduler, cancel, resultZ -> {
                if (resultZ instanceof Cancelled<Z> cancelledZ) {
                    ultimateCallback.call(cancelledZ.contort());
                } else if (resultZ instanceof Success<Z> successZ) {
                    Fiber<A> fiberA = bind.f().apply(successZ.result());
                    if (fiberA instanceof Bind<?, A> bindA) {
                        if (stackDepth == stackFrameTransplantDepth)
                            scheduler.schedule(() -> bindA.execute(scheduler, cancel, ultimateCallback));
                        else {
                            tick(bindA, scheduler, ultimateCallback, cancel, stackDepth + 1);
                        }
                    } else {
                        if (stackDepth == stackFrameTransplantDepth)
                            scheduler.schedule(() -> fiberA.execute(scheduler, cancel, ultimateCallback));
                        else
                            fiberA.execute(scheduler, cancel, ultimateCallback);
                    }
                } else {
                    ultimateCallback.call(((Failure<Z>) resultZ).contort());
                }
            });
        }
    }
}