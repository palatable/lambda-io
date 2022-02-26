package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.adt.choice.Choice2;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberCallback;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.Cancelled;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.Failure;
import com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.Success;
import com.jnape.palatable.lambda.effect.io.fiber2.old.Scheduler;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;
import com.jnape.palatable.lambda.runtime.fiber.Canceller;
import com.jnape.palatable.lambda.runtime.fiber.internal.Array;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.adt.choice.Choice2.a;
import static com.jnape.palatable.lambda.adt.choice.Choice2.b;
import static com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.cancelled;
import static com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.failure;
import static com.jnape.palatable.lambda.effect.io.fiber2.old.FiberResult.success;

public interface Fiber<A> {

    Fiber<?> NEVER = (s, c, k) -> {};

    void execute(Scheduler scheduler, Canceller cancel, FiberCallback<A> callback);

    static <A> Fiber<A> fiber(Fn0<? extends A> f) {
        return cancellable((scheduler, callback) -> {
            try {callback.call(success(f.apply()));} catch (Throwable t) {callback.call(failure(t));}
        });
    }

    static Fiber<Unit> fiber(SideEffect sideEffect) {
        return fiber(() -> {
            sideEffect.Ω();
            return UNIT;
        });
    }

    static <A> Fiber<A> timeout(Duration duration, Fiber<A> fiber) {
        return (s, c, k) -> s.schedule(() -> race(fiber, park(duration)).execute(s, c, res -> {
            if (c.cancelled() || res instanceof Cancelled<?>)
                k.call(cancelled());
            else
                k.call(res instanceof Success<Choice2<A, Unit>> success
                       ? success.result().match(FiberResult::success,
                                                timedOut -> cancelled())
                       : ((Failure<A>) res).contort());

        }));
    }

    static <A, B> Fiber<Choice2<A, B>> race(Fiber<A> a, Fiber<B> b) {
        return (s, c, k) -> {
            if (c.cancelled())
                k.call(cancelled());
            else {
                AtomicBoolean flag  = new AtomicBoolean(false);
                Canceller        child = c.addChild();
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

    @SafeVarargs
    static <A> Fiber<Array<A>> parallel(Fiber<A>... fibers) {
        return (s, c, k) -> s.schedule(() -> {
            if (c.cancelled()) {
                k.call(cancelled());
            } else {
                Object[]      results   = new Object[fibers.length];
                Canceller        shared    = c.addChild();
                AtomicInteger remaining = new AtomicInteger(fibers.length);
                for (int i = 0; i < fibers.length; i++) {
                    int finalI = i;
                    s.schedule(() -> fibers[finalI].execute(s, shared, res -> {
                        if (res instanceof Cancelled<?> cancelled) {
                            if (remaining.getAndSet(-1) > 0)
                                k.call(cancelled.contort());
                        } else if (res instanceof Success<A> success) {
                            results[finalI] = success.result();
                            if (c.cancelled() && remaining.getAndSet(-1) > 0)
                                k.call(cancelled());
                            else if (remaining.decrementAndGet() == 0) {
                                if (c.cancelled()) k.call(cancelled());
                                else {
                                    @SuppressWarnings("unchecked")
                                    Array<A> array = (Array<A>) Array.shallowCopy(results);
                                    k.call(success(array));
                                }
                            }
                        } else {
                            Failure<A> failure = (Failure<A>) res;
                            if (remaining.getAndSet(-1) > 0) {
                                shared.cancel();
                                k.call(c.cancelled() ? cancelled() : failure.contort());
                            }
                        }
                    }));
                }
            }
        });
    }

    static <A> Fiber<A> fail(Throwable t) {
        return cancellable(c -> c.call(failure(t)));
    }

    static Fiber<Unit> park(Duration duration) {
        return (s, c, k) -> c.onCancel(s.delay(duration, () -> k.call(c.cancelled()
                                                                      ? cancelled()
                                                                      : success(UNIT)))
                                               ::cancel);
    }

    default <B> Fiber<B> bind(Fn1<? super A, ? extends Fiber<B>> f) {
        return new Bind<>(this, f);
    }

    public static <A, B> Fiber<B> forever(Fiber<A> fiber) {
        return fiber.bind(__ -> forever(fiber));
    }

    @SuppressWarnings("unchecked")
    static <A> Fiber<A> never() {
        return (Fiber<A>) NEVER;
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
}

record Bind<Z, A>(Fiber<Z> fiberZ, Fn1<? super Z, ? extends Fiber<A>> f) implements Fiber<A> {

    interface Eliminator<A> {
        <Z> void apply(Fiber<Z> fiberZ, Fn1<? super Z, ? extends Fiber<A>> f);
    }

    void eliminate(Eliminator<A> eliminator) {
        eliminator.apply(fiberZ, f);
    }

    @Override
    public void execute(Scheduler scheduler, Canceller cancel, FiberCallback<A> callback) {
        tick(this, scheduler, callback, cancel, 1);
    }

    private static final int stackFrameTransplantDepth = 512;

    public static <Z, A> void tick(Bind<Z, A> bind, Scheduler scheduler, FiberCallback<A> ultimateCallback,
                                   Canceller cancel, int stackDepth) {
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
                    try {
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
                    } catch (Throwable t) {
                        ultimateCallback.call(failure(new ExceptionOutsideOfFiber(t)));
                    }
                } else {
                    ultimateCallback.call(((Failure<Z>) resultZ).contort());
                }
            });
        }
    }
}