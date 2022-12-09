package research.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;
import com.jnape.palatable.lambda.effect.io.fiber.Result;
import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import research.lambda.runtime.fiber.Canceller;
import research.lambda.runtime.fiber.internal.Array;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;

public interface Fiber<A> {

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

}

record Bind<Z, A>(Fiber<Z> fiberZ, Fn1<? super Z, ? extends Fiber<A>> f) implements Fiber<A> {

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
        bind.fiberZ().execute(scheduler, cancel, resultZ -> {
            if (resultZ instanceof Result.Cancellation<Z>) {
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