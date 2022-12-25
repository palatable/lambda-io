package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;

//todo: Fiber.pin
//todo: configurable maxStackDepth
public final class Trampoline implements Runtime {
    private static final int maxStackDepth = 512;

    private final Supplier<Canceller> freshCanceller;
    private final Scheduler           defaultScheduler;
    private final Timer               timer;

    private Trampoline(Supplier<Canceller> freshCanceller, Scheduler defaultScheduler, Timer timer) {
        this.freshCanceller   = freshCanceller;
        this.defaultScheduler = defaultScheduler;
        this.timer            = timer;
    }

    @Override
    public <A> void unsafeRunAsync(Fiber<A> fiber, Consumer<? super Result<A>> callback) {
        schedule(fiber, freshCanceller.get(), (__, res) -> callback.accept(res));
    }

    private <A> void tick(Fiber<A> fiber, Canceller canceller, BiConsumer<? super Integer, ? super Result<A>> callback,
                          int stackDepth) {
        //todo: consider scheduling this check every n ticks to boost performance
        if (canceller.cancelled()) {
            callback.accept(stackDepth, cancellation());
        } else {
            if (stackDepth == maxStackDepth) {
                schedule(fiber, canceller, callback);
                return;
            }

            //todo: compare with switch and enum
            if (fiber instanceof Value<A> value) {
                callback.accept(stackDepth, value.result());
            } else if (fiber instanceof Suspension<A> suspension) {
                //todo: should this be wrapped in try/catch and re-throw as critical error?
                suspension.k().accept((Consumer<Result<A>>) res -> callback.accept(stackDepth, res));
            } else if (fiber instanceof Forever<A> forever) {
                forever(forever, canceller, callback, stackDepth);
            } else if (fiber instanceof Race<A> race) {
                race(canceller, callback, race);
            } else if (fiber instanceof Bind<?, A> bind) {
                bind(bind, canceller, callback, stackDepth);
            } else if (fiber instanceof Delay<A> delay) {
                Runnable cancel = timer.delay(() -> schedule(delay.fiber(), canceller, callback),
                                              delay.delay(), delay.timeUnit());
                if (!canceller.onCancellation(cancel))
                    cancel.run();
            } // else Never
        }
    }

    private <A> void race(Canceller canceller, BiConsumer<? super Integer, ? super Result<A>> callback,
                          Race<A> race) {
        Canceller     child  = canceller.addChild();
        AtomicBoolean winner = new AtomicBoolean(true);
        for (Fiber<A> fiber : List.of(race.fiberA(), race.fiberB())) {
            schedule(fiber, child, (stackDepth, res) -> {
                if (winner.getAndSet(false)) {
                    child.cancel();
                    callback.accept(stackDepth + 1, res);
                }
            });
        }
    }

    private <A> void forever(Forever<A> forever, Canceller canceller,
                             BiConsumer<? super Integer, ? super Result<A>> callback,
                             int stackDepth) {
        tick(forever.fiber(), canceller, new BiConsumer<Integer, Result<?>>() {
            @Override
            public void accept(Integer sd, Result<?> res) {
                if (res instanceof Result.Success<?>)
                    tick(forever.fiber(), canceller, this, sd + 1);
                else if (res instanceof Result.Failure<?> f)
                    callback.accept(sd, f.contort());
                else
                    callback.accept(sd, cancellation());
            }
        }, stackDepth + 1);
    }

    private <A> void schedule(Fiber<A> fiber, Canceller canceller,
                              BiConsumer<? super Integer, ? super Result<A>> callback) {
        defaultScheduler.schedule(() -> tick(fiber, canceller, callback, 0));
    }

    private <A> void bind(Bind<?, A> bind, Canceller canceller,
                          BiConsumer<? super Integer, ? super Result<A>> callback, int stackDepth) {
        tick0((Bind<?, A>) (bind.fiberZ() instanceof Bind<?, ?> ? bind.rightAssociated() : bind),
              canceller, callback, stackDepth);
    }

    private <X, A> void tick0(Bind<X, A> bind, Canceller canceller,
                              BiConsumer<? super Integer, ? super Result<A>> finalCallback, int stackDepth) {
        tick(bind.fiberZ(), canceller, (sd, resX) -> {
            if (resX instanceof Result.Success<X> success) {
                tick(bind.f().apply(success.value()), canceller, finalCallback, sd + 1);
            } else if (resX instanceof Result.Failure<X> failure) {
                finalCallback.accept(sd, failure.contort());
            } else {
                finalCallback.accept(sd, cancellation());
            }
        }, stackDepth + 1);
    }

    public static Trampoline trampoline(Supplier<Canceller> freshCanceller, Scheduler defaultScheduler, Timer timer) {
        return new Trampoline(freshCanceller, defaultScheduler, timer);
    }

    public static Trampoline trampoline(Scheduler defaultScheduler, Timer timer) {
        return trampoline(Canceller::canceller, defaultScheduler, timer);
    }
}
