package research.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.adt.choice.Choice2;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;
import research.lambda.runtime.fiber.Canceller;
import research.lambda.runtime.fiber.Result;
import com.jnape.palatable.lambda.runtime.fiber.Scheduler;
import research.lambda.runtime.fiber.internal.Array;

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
        return (scheduler, c, callback) -> callback.accept(Result.success(f.apply()));
    }

    static Fiber<Unit> fiber(SideEffect sideEffect) {
        return fiber(() -> {
            sideEffect.Ω();
            return UNIT;
        });
    }

    static <A, B> Fiber<Choice2<A, B>> race(Fiber<A> a, Fiber<B> b) {
        return (s, c, k) -> {
            if (c.cancelled()) k.accept(Result.cancellation());
            else {
                AtomicBoolean flag  = new AtomicBoolean(false);
                Canceller     child = c.addChild();
                s.schedule(() -> a.execute(s, child, res -> {
                    if (!flag.getAndSet(true)) {
                        child.cancel();
                        k.accept(c.cancelled() || res instanceof Result.Cancellation<?> ? Result.cancellation() : res instanceof Result.Success<A> success ? Result.success(a(success.value())) : ((Result.Failure<?>) res).contort());
                    }
                }));
                s.schedule(() -> b.execute(s, child, res -> {
                    if (!flag.getAndSet(true)) {
                        child.cancel();
                        k.accept(c.cancelled() || res instanceof Result.Cancellation<?> ? Result.cancellation() : res instanceof Result.Success<B> success ? Result.success(b(success.value())) : ((Result.Failure<?>) res).contort());
                    }
                }));
            }
        };
    }

    @SafeVarargs
    static <A> Fiber<Array<A>> parallel(Fiber<A>... fibers) {
        return (s, c, k) -> s.schedule(() -> {
            if (c.cancelled()) {
                k.accept(Result.cancellation());
            } else {
                Object[]      results   = new Object[fibers.length];
                Canceller     shared    = c.addChild();
                AtomicInteger remaining = new AtomicInteger(fibers.length);
                for (int i = 0; i < fibers.length; i++) {
                    int finalI = i;
                    s.schedule(() -> fibers[finalI].execute(s, shared, res -> {
                        if (res instanceof Result.Cancellation<?>) {
                            if (remaining.getAndSet(-1) > 0) k.accept(Result.cancellation());
                        } else if (res instanceof Result.Success<A> success) {
                            results[finalI] = success.value();
                            if (c.cancelled() && remaining.getAndSet(-1) > 0) k.accept(Result.cancellation());
                            else if (remaining.decrementAndGet() == 0) {
                                if (c.cancelled()) k.accept(Result.cancellation());
                                else {
                                    @SuppressWarnings("unchecked") Array<A> array = (Array<A>) Array.shallowCopy(results);
                                    k.accept(Result.success(array));
                                }
                            }
                        } else {
                            Result.Failure<A> failure = (Result.Failure<A>) res;
                            if (remaining.getAndSet(-1) > 0) {
                                shared.cancel();
                                k.accept(c.cancelled() ? Result.cancellation() : failure.contort());
                            }
                        }
                    }));
                }
            }
        });
    }

    static <A> Fiber<A> fail(Throwable t) {
        return cancellable(c -> c.accept(Result.failure(t)));
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
                callback.accept(Result.cancellation());
            } else task.accept(scheduler, callback);
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
            ultimateCallback.accept(Result.cancellation());
            return;
        }
        if (bind.fiberZ() instanceof Bind<?, Z> bindZ) {
            bindZ.eliminate(new Eliminator<>() {
                @Override
                public <Y> void apply(Fiber<Y> fiberY, Fn1<? super Y, ? extends Fiber<Z>> g) {
                    if (stackDepth == stackFrameTransplantDepth) scheduler.schedule(() -> new Bind<>(fiberY, y -> new Bind<>(g.apply(y), bind.f())).execute(scheduler, cancel, ultimateCallback));
                    else {
                        tick(new Bind<>(fiberY, y -> new Bind<>(g.apply(y), bind.f())), scheduler, ultimateCallback, cancel, stackDepth + 1);
                    }
                }
            });
        } else {
            bind.fiberZ().execute(scheduler, cancel, resultZ -> {
                if (resultZ instanceof Result.Cancellation<Z> cancellationZ) {
                    ultimateCallback.accept(Result.cancellation());
                } else if (resultZ instanceof Result.Success<Z> successZ) {
                    try {
                        Fiber<A> fiberA = bind.f().apply(successZ.value());
                        if (fiberA instanceof Bind<?, A> bindA) {
                            if (stackDepth == stackFrameTransplantDepth) scheduler.schedule(() -> bindA.execute(scheduler, cancel, ultimateCallback));
                            else {
                                tick(bindA, scheduler, ultimateCallback, cancel, stackDepth + 1);
                            }
                        } else {
                            if (stackDepth == stackFrameTransplantDepth) scheduler.schedule(() -> fiberA.execute(scheduler, cancel, ultimateCallback));
                            else fiberA.execute(scheduler, cancel, ultimateCallback);
                        }
                    } catch (Throwable t) {
                        ultimateCallback.accept(Result.failure(new ExceptionOutsideOfFiber(t)));
                    }
                } else {
                    ultimateCallback.accept(((Result.Failure<Z>) resultZ).contort());
                }
            });
        }
    }
}