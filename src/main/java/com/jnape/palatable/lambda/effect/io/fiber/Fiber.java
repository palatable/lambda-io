package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.adt.choice.Choice2;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.Cancelled;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.Failure;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.NonCancelledResult;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.Success;
import com.jnape.palatable.lambda.functions.Fn1;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.effect.io.fiber.FiberResult.cancelled;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Constantly.constantly;

public interface Fiber<A> {

    void call(Scheduler s, Cancel c, FiberCallback<A> k);

    default <B> Fiber<A> suspend(Fiber<B> blocker) {
        return blocker.bind(constantly(this));
    }

    default <B> Fiber<B> bind(Fn1<? super A, ? extends Fiber<B>> f) {
        return (s, c, k) -> {
            if (c.cancelled())
                k.call(cancelled());
            else {
                try {
                    call(s, c, res -> {
                        if (c.cancelled() || res instanceof Cancelled<?>)
                            k.call(cancelled());
                        else if (res instanceof Success<A> success)
                            f.apply(success.result()).call(s, c, k);
                        else k.call(FiberResult.failure(((Failure<A>) res).ex()));
                    });
                } catch (Throwable t) {
                    k.call(new Failure<>(t));
                }
            }
        };
    }

    default <B> Fiber<B> mapResult(Fn1<? super NonCancelledResult<A>, ? extends NonCancelledResult<B>> fn) {
        return (s, c, k) -> {
            if (c.cancelled())
                k.call(cancelled());
            else
                try {
                    call(s, c, fiberResult -> {
                        if (c.cancelled())
                            k.call(cancelled());
                        else
                            k.call(fiberResult.mapResult(fn));
                    });
                } catch (Throwable t) {
                    k.call(FiberResult.failure(t));
                }
        };
    }

    default <B> Fiber<B> map(Fn1<? super A, ? extends B> f) {
        return mapResult(res -> res.mapResult(r -> r instanceof Success<A> success
                                                   ? new Success<>(f.apply(success.result()))
                                                   : ((Failure<A>) r).contort()));
    }

    default Fiber<A> recover(Fn1<? super Throwable, ? extends A> f) {
        return mapResult(res -> res.mapResult(r -> (r instanceof Failure<A> failure)
                                                   ? new Success<>(f.apply(failure.ex()))
                                                   : r));
    }

    static <A> Fiber<A> success(A a) {
        return (s, c, k) -> k.call(c.cancelled() ? cancelled() : FiberResult.success(a));
    }

    static <A> Fiber<A> fail(Throwable t) {
        return (s, c, k) -> k.call(c.cancelled() ? cancelled() : FiberResult.failure(t));
    }

    static Fiber<Unit> delay(Duration duration) {
        return (s, c, k) -> {
            CancelToken token = s.delay(duration, () -> k.call(c.cancelled()
                                                               ? cancelled()
                                                               : FiberResult.success(UNIT)));
            c.onCancel(token::cancel);
        };
    }

    @SafeVarargs
    static <A> Fiber<Collection<A>> parallel(Fiber<A>... fibers) {
        return (s, c, k) -> {
            if (c.cancelled())
                k.call(cancelled());
            else {
                Cancel        child     = c.addChild();
                AtomicInteger remaining = new AtomicInteger(fibers.length);
                Object[]      successes = new Object[fibers.length];
                for (int i = 0; i < fibers.length; i++) {
                    Fiber<A> fiber  = fibers[i];
                    int      finalI = i;
                    s.schedule(() -> fiber.call(s, child, res -> {
                        if (res instanceof Cancelled<?>) {
                            if (remaining.getAndSet(-1) > 0)
                                k.call(cancelled());
                        } else if (res instanceof Success<A> success) {
                            successes[finalI] = success.result();
                            if (c.cancelled() && remaining.getAndSet(-1) > 0)
                                k.call(cancelled());
                            else if (remaining.decrementAndGet() == 0) {
                                if (c.cancelled()) k.call(cancelled());
                                else {
                                    @SuppressWarnings("unchecked")
                                    Collection<A> coll = (Collection<A>) Arrays.asList(successes);
                                    k.call(FiberResult.success(coll));
                                }
                            }
                        } else {
                            Failure<A> failure = (Failure<A>) res;
                            if (remaining.getAndSet(-1) > 0) {
                                child.cancel();
                                k.call(c.cancelled() ? cancelled() : failure.contort());
                            }
                        }
                    }));
                }
            }
        };
    }

    static <A, B> Fiber<Choice2<A, B>> race(Fiber<A> a, Fiber<B> b) {
        return (s, c, k) -> {
            if (c.cancelled())
                k.call(cancelled());
            else {
                AtomicBoolean flag  = new AtomicBoolean(false);
                Cancel        child = c.addChild();
                s.schedule(() -> a.call(s, child, res -> {
                    if (!flag.getAndSet(true)) {
                        child.cancel();
                        k.call(c.cancelled() || res instanceof Cancelled<?>
                               ? cancelled()
                               : res instanceof Success<A> success
                                 ? FiberResult.success(Choice2.a(success.result()))
                                 : ((Failure<?>) res).contort());
                    }
                }));
                s.schedule(() -> b.call(s, child, res -> {
                    if (!flag.getAndSet(true)) {
                        child.cancel();
                        k.call(c.cancelled() || res instanceof Cancelled<?>
                               ? cancelled()
                               : res instanceof Success<B> success
                                 ? FiberResult.success(Choice2.b(success.result()))
                                 : ((Failure<?>) res).contort());
                    }
                }));
            }
        };
    }

    static <A> Fiber<A> timeout(Duration duration, Fiber<A> fiber) {
        return (s, c, k) -> race(delay(duration), fiber).call(s, c, res -> {
            if (c.cancelled() || res instanceof Cancelled<?>)
                k.call(cancelled());
            else
                k.call(res instanceof Success<Choice2<Unit, A>> success
                       ? success.result().match(timedOut -> cancelled(),
                                                FiberResult::success)
                       : ((Failure<A>) res).contort());

        });
    }

    static <A> Fiber<A> ensuring(Fiber<A> fiber, Fiber<?> noMatterWhatAfter) {
        return (s, c, k) -> {
            AtomicBoolean run = new AtomicBoolean(false);
            c.onCancel(() -> {
                if (!run.getAndSet(true))
                    noMatterWhatAfter.call(s, c, __ -> k.call(cancelled()));
            });
            fiber.call(s, c, res -> {
                if (!run.getAndSet(true))
                    noMatterWhatAfter.call(s, c, __ -> k.call(res));
            });
        };
    }

    static <A> Fiber<A> fiber(Fiber<A> fiber) {
        return fiber;
    }

    static <A> Fiber<A> never() {
        return (s, c, k) -> {};
    }
}
