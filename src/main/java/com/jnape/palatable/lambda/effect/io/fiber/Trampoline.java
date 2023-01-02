package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.result;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;

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
        schedule(fiber, defaultScheduler, freshCanceller.get(), (__, ___, res) -> callback.accept(res));
    }

    private <A> void tick(Fiber<A> fiber, Scheduler scheduler, Canceller canceller, Continuation<A> continuation,
                          int stackDepth) {
        //todo: consider scheduling this check every n ticks to boost performance
        if (canceller.cancelled()) {
            continuation.accept(stackDepth, scheduler, cancellation());
        } else {
            if (stackDepth == maxStackDepth) {
                schedule(fiber, scheduler, canceller, continuation);
                return;
            }

            //todo: compare with switch and enum
            //todo: adding cases to this else-if switch eventually nukes optimized performance; why?
            //      - branch predication?
            //      - de-optimized byte code? (e.g. too many jumps, table switch for trivially few conditionals, etc.)
            //      - code cache?
            //      - something else?
            if (fiber instanceof Value<A> value) {
                continuation.accept(stackDepth, scheduler, value.result());
            } else if (fiber instanceof Suspension<A> suspension) {
                //todo: should this be wrapped in try/catch and re-throw as critical error?
                suspension.k().accept((Consumer<Result<A>>) res -> continuation.accept(stackDepth, scheduler, res));
            } else if (fiber instanceof Forever<A> forever) {
                forever(forever, scheduler, canceller, continuation, stackDepth);
            } else if (fiber instanceof Race<A> race) {
                race(race, scheduler, canceller, continuation);
            } else if (fiber instanceof Bind<?, A> bind) {
                bind(bind, scheduler, canceller, continuation, stackDepth);
            } else if (fiber instanceof Delay<A> delay) {
                delay(delay, scheduler, canceller, continuation);
            } else if (fiber instanceof Pin<A> pin) {
                pin(pin, scheduler, canceller, continuation, stackDepth);
            } // else Never
        }
    }

    private <A> void pin(Pin<A> pin, Scheduler scheduler, Canceller canceller, Continuation<A> continuation,
                         int stackDepth) {
        if (pin.scheduler() == scheduler) {
            tick(pin.fiber(), scheduler, canceller, continuation, stackDepth + 1);
        } else {
            schedule(pin.fiber(), pin.scheduler(), canceller,
                     (sd, __, res) -> schedule(result(res), scheduler, canceller, continuation));
        }
    }

    private <A> void delay(Delay<A> delay, Scheduler scheduler, Canceller canceller, Continuation<A> continuation) {
        Runnable cancel = timer.delay(() -> schedule(delay.fiber(), scheduler, canceller, continuation),
                                      delay.delay(), delay.timeUnit());
        if (!canceller.onCancellation(cancel))
            cancel.run();
    }

    private <A> void race(Race<A> race, Scheduler scheduler, Canceller canceller, Continuation<A> continuation) {
        Canceller     child  = canceller.addChild();
        AtomicBoolean winner = new AtomicBoolean(true);
        for (Fiber<A> fiber : List.of(race.fiberA(), race.fiberB())) {
            schedule(fiber, scheduler, child, (stackDepth, sch, res) -> {
                if (winner.getAndSet(false)) {
                    child.cancel();
                    continuation.accept(stackDepth + 1, sch, res);
                }
            });
        }
    }

    private <A> void forever(Forever<A> forever, Scheduler scheduler, Canceller canceller,
                             Continuation<A> continuation, int stackDepth) {
        @SuppressWarnings("unchecked")
        Fiber<Object> fiber = (Fiber<Object>) forever.fiber();
        tick(fiber, scheduler, canceller, new Continuation<>() {
            @Override
            public void accept(Integer sd, Scheduler sch, Result<Object> res) {
                if (res instanceof Result.Success<?>)
                    tick(fiber, sch, canceller, this, sd + 1);
                else if (res instanceof Result.Failure<?> f)
                    continuation.accept(sd, sch, f.contort());
                else
                    continuation.accept(sd, sch, cancellation());
            }
        }, stackDepth + 1);
    }

    private <A> void schedule(Fiber<A> fiber, Scheduler scheduler, Canceller canceller,
                              Continuation<A> continuation) {
        scheduler.schedule(() -> tick(fiber, scheduler, canceller, continuation, 0));
    }

    private <A> void bind(Bind<?, A> bind, Scheduler scheduler, Canceller canceller,
                          Continuation<A> continuation, int stackDepth) {
        tick0((Bind<?, A>) (bind.fiberZ() instanceof Bind<?, ?> ? bind.rightAssociated() : bind),
              scheduler, canceller, continuation, stackDepth);
    }

    private <X, A> void tick0(Bind<X, A> bind, Scheduler scheduler, Canceller canceller,
                              Continuation<A> continuation, int stackDepth) {
        tick(bind.fiberZ(), scheduler, canceller, (sd, sch, resX) -> {
            if (resX instanceof Result.Success<X> success) {
                tick(bind.f().apply(success.value()), sch, canceller, continuation, sd + 1);
            } else if (resX instanceof Result.Failure<X> failure) {
                continuation.accept(sd, sch, failure.contort());
            } else {
                continuation.accept(sd, sch, cancellation());
            }
        }, stackDepth + 1);
    }

    public static Trampoline trampoline(Supplier<Canceller> freshCanceller, Scheduler defaultScheduler, Timer timer) {
        return new Trampoline(freshCanceller, defaultScheduler, timer);
    }

    public static Trampoline trampoline(Scheduler defaultScheduler, Timer timer) {
        return trampoline(Canceller::canceller, defaultScheduler, timer);
    }

    interface Continuation<A> {
        void accept(Integer stackDepth, Scheduler scheduler, Result<A> result);
    }
}
