package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.adt.choice.Choice2;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;
import com.jnape.palatable.lambda.runtime.fiber.Canceller;
import com.jnape.palatable.lambda.runtime.fiber.Result;
import com.jnape.palatable.lambda.runtime.fiber.Scheduler;
import com.jnape.palatable.lambda.runtime.fiber.internal.Array;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.adt.choice.Choice2.a;
import static com.jnape.palatable.lambda.adt.choice.Choice2.b;

public interface Fiber<A> {

    Fiber<?> NEVER = (s, c, k) -> {};

    void execute(Scheduler scheduler, Canceller cancel, Consumer<? super Result<A>> callback);

    static <A> Fiber<A> fiber(Fn0<? extends A> f) {
        return (scheduler, c, callback) -> callback.accept(Result.successful(f.apply()));
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
                k.accept(Result.cancelled());
            else {
                AtomicBoolean flag  = new AtomicBoolean(false);
                Canceller     child = c.addChild();
                s.schedule(() -> a.execute(s, child, res -> {
                    if (!flag.getAndSet(true)) {
                        child.cancel();
                        k.accept(c.cancelled() || res instanceof Result.Cancelled<?>
                                 ? Result.cancelled()
                                 : res instanceof Result.Successful<A> success
                                   ? Result.successful(a(success.value()))
                                   : ((Result.Failed<?>) res).contort());
                    }
                }));
                s.schedule(() -> b.execute(s, child, res -> {
                    if (!flag.getAndSet(true)) {
                        child.cancel();
                        k.accept(c.cancelled() || res instanceof Result.Cancelled<?>
                                 ? Result.cancelled()
                                 : res instanceof Result.Successful<B> success
                                   ? Result.successful(b(success.value()))
                                   : ((Result.Failed<?>) res).contort());
                    }
                }));
            }
        };
    }

    @SafeVarargs
    static <A> Fiber<Array<A>> parallel(Fiber<A>... fibers) {
        return (s, c, k) -> s.schedule(() -> {
            if (c.cancelled()) {
                k.accept(Result.cancelled());
            } else {
                Object[]      results   = new Object[fibers.length];
                Canceller     shared    = c.addChild();
                AtomicInteger remaining = new AtomicInteger(fibers.length);
                for (int i = 0; i < fibers.length; i++) {
                    int finalI = i;
                    s.schedule(() -> fibers[finalI].execute(s, shared, res -> {
                        if (res instanceof Result.Cancelled<?> cancelled) {
                            if (remaining.getAndSet(-1) > 0)
                                k.accept(cancelled.contort());
                        } else if (res instanceof Result.Successful<A> success) {
                            results[finalI] = success.value();
                            if (c.cancelled() && remaining.getAndSet(-1) > 0)
                                k.accept(Result.cancelled());
                            else if (remaining.decrementAndGet() == 0) {
                                if (c.cancelled()) k.accept(Result.cancelled());
                                else {
                                    @SuppressWarnings("unchecked")
                                    Array<A> array = (Array<A>) Array.shallowCopy(results);
                                    k.accept(Result.successful(array));
                                }
                            }
                        } else {
                            Result.Failed<A> failure = (Result.Failed<A>) res;
                            if (remaining.getAndSet(-1) > 0) {
                                shared.cancel();
                                k.accept(c.cancelled() ? Result.cancelled() : failure.contort());
                            }
                        }
                    }));
                }
            }
        });
    }

    static <A> Fiber<A> fail(Throwable t) {
        return cancellable(c -> c.accept(Result.failed(t)));
    }


    default <B> Fiber<B> bind(Fn1<? super A, ? extends Fiber<B>> f) {
        return new Bind<>(this, f);
    }

    static <A, B> Fiber<B> forever(Fiber<A> fiber) {
        return fiber.bind(__ -> forever(fiber));
    }

    @SuppressWarnings("unchecked")
    static <A> Fiber<A> never() {
        return (Fiber<A>) NEVER;
    }

    static <A> Fiber<A> cancellable(BiConsumer<? super Scheduler, ? super Consumer<? super Result<A>>> task) {
        return (scheduler, cancel, callback) -> {
            if (cancel.cancelled()) {
                callback.accept(Result.cancelled());
            } else
                task.accept(scheduler, callback);
        };
    }

    static <A> Fiber<A> cancellable(Consumer<? super Consumer<? super Result<A>>> task) {
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
    public void execute(Scheduler scheduler, Canceller cancel, Consumer<? super Result<A>> callback) {
        tick(this, scheduler, callback, cancel, 1);
    }

    private static final int stackFrameTransplantDepth = 512;

    public static <Z, A> void tick(Bind<Z, A> bind, Scheduler scheduler, Consumer<? super Result<A>> ultimateCallback,
                                   Canceller cancel, int stackDepth) {
        if (cancel.cancelled()) {
            ultimateCallback.accept(Result.cancelled());
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
                if (resultZ instanceof Result.Cancelled<Z> cancelledZ) {
                    ultimateCallback.accept(cancelledZ.contort());
                } else if (resultZ instanceof Result.Successful<Z> successZ) {
                    try {
                        Fiber<A> fiberA = bind.f().apply(successZ.value());
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
                        ultimateCallback.accept(Result.failed(new ExceptionOutsideOfFiber(t)));
                    }
                } else {
                    ultimateCallback.accept(((Result.Failed<Z>) resultZ).contort());
                }
            });
        }
    }
}