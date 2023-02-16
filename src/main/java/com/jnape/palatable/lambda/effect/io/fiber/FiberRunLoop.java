package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.effect.io.fiber.Result.Failure;
import com.jnape.palatable.lambda.effect.io.fiber.Result.Success;
import com.jnape.palatable.lambda.effect.io.fiber.Result.Unsuccessful;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.result;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.succeeded;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;
import static java.util.Arrays.asList;

public final class FiberRunLoop implements Runtime {

    private final Supplier<Canceller> cancellerFactory;
    private final Executor            defaultExecutor;
    private final Timer               timer;
    private final int                 maxTicksBeforePreemption;

    private FiberRunLoop(Supplier<Canceller> cancellerFactory, Executor defaultExecutor, Timer timer,
                         int maxTicksBeforePreemption) {
        this.cancellerFactory         = cancellerFactory;
        this.defaultExecutor          = defaultExecutor;
        this.timer                    = timer;
        this.maxTicksBeforePreemption = maxTicksBeforePreemption;
    }

    @Override
    public <A> void schedule(Fiber<A> fiber, Consumer<? super Result<A>> callback) {
        //todo: wrap callback.accept in a try/catch?
        preempt(fiber, defaultExecutor, cancellerFactory.get(), Continuation.root(callback));
    }

    private <A> void tick(Fiber<A> fiber, Executor executor, Canceller canceller, Continuation<A> continuation,
                          int stackDepth) {
        //todo: consider scheduling this check every n ticks to boost performance
        if (canceller.cancelled()) {
            continuation.accept(stackDepth, executor, cancellation());
        } else {
            if (stackDepth == maxTicksBeforePreemption) {
                preempt(fiber, executor, canceller, continuation);
                return;
            }

            //todo: compare with switch and enum
            //todo: adding cases to this else-if switch eventually nukes optimized performance; why?
            //      - branch predication?
            //      - de-optimized byte code? (e.g. too many jumps, table switch for trivially few conditionals, etc.)
            //      - code cache?
            //      - something else?
            if (fiber instanceof Value<A> value) {
                continuation.accept(stackDepth, executor, value.result());
            }
            //todo: env variable to determine whether this is treated as blocking or non-blocking by default for upgrade path?
            else if (fiber instanceof Suspension<A> suspension) {
                //todo: should this be wrapped in try/catch and re-throw as critical error?
                suspension.k().accept((Consumer<Result<A>>) res -> continuation.accept(stackDepth, executor, res));
            } else if (fiber instanceof Forever<?, A> forever) {
                forever(forever, executor, canceller, continuation, stackDepth);
            } else if (fiber instanceof Bind<?, A> bind) {
                bind(bind, executor, canceller, continuation, stackDepth);
            } else if (fiber instanceof Pin<A> pin) {
                pin(pin, executor, canceller, continuation, stackDepth);
            } else if (fiber instanceof Delay<A> delay) {
                delay(delay, executor, canceller, continuation);
            } else if (fiber instanceof Race<A> race) {
                race(race, executor, canceller, continuation);
            } else if (fiber instanceof Parallel<?, A> parallel) {
                parallel(parallel, executor, canceller, continuation);
            }
        }
    }

    private <A> void pin(Pin<A> pin, Executor executor, Canceller canceller, Continuation<A> continuation,
                         int stackDepth) {
        if (pin.executor() == executor) {
            tick(pin.fiber(), executor, canceller, continuation, stackDepth + 1);
        } else {
            preempt(pin.fiber(), pin.executor(), canceller,
                    (sd, __, res) -> preempt(result(res), executor, canceller, continuation));
        }
    }

    private <A> void delay(Delay<A> delay, Executor executor, Canceller canceller, Continuation<A> continuation) {
        //todo: if delay is 0, just keep ticking
        Runnable cancel = timer.delay(() -> preempt(delay.fiber(), executor, canceller, continuation),
                                      delay.delay(), delay.timeUnit());
        if (!canceller.onCancellation(cancel))
            cancel.run();
    }

    private <A> void race(Race<A> race, Executor executor, Canceller canceller, Continuation<A> continuation) {
        Canceller     child  = canceller.addChild();
        AtomicBoolean winner = new AtomicBoolean(true);
        for (Fiber<A> fiber : race.fibers()) {
            preempt(fiber, executor, child, (stackDepth, ex, res) -> {
                if (winner.getAndSet(false)) {
                    child.cancel();
                    tick(result(res), ex, canceller, continuation, stackDepth + 1);
                }
            });
        }
    }

    private <X, A> void forever(Forever<X, A> forever, Executor executor, Canceller canceller,
                                Continuation<A> continuation, int stackDepth) {
        Fiber<X> fiber = forever.fiber();
        tick(fiber, executor, canceller, new Continuation<>() {
            @Override
            public void accept(Integer sd, Executor ex, Result<X> res) {
                if (res instanceof Success<?>)
                    tick(fiber, ex, canceller, this, sd + 1);
                else
                    tick(result(((Unsuccessful<?>) res).contort()), ex, canceller, continuation, sd + 1);
            }
        }, stackDepth + 1);
    }

    private <A> void preempt(Fiber<A> fiber, Executor executor, Canceller canceller,
                             Continuation<A> continuation) {
        executor.execute(() -> tick(fiber, executor, canceller, continuation, 0));
    }

    private <A> void bind(Bind<?, A> bind, Executor executor, Canceller canceller,
                          Continuation<A> continuation, int stackDepth) {
        tick0((Bind<?, A>) (bind.fiberZ() instanceof Bind<?, ?> ? bind.rightAssociated() : bind),
              executor, canceller, continuation, stackDepth);
    }

    private <X, A> void tick0(Bind<X, A> bind, Executor executor, Canceller canceller,
                              Continuation<A> continuation, int stackDepth) {
        //todo: tick0 here? we're already guaranteed to be rightAssoc...
        tick(bind.fiberZ(), executor, canceller, (sd, ex, resX) ->
                     tick(resX instanceof Success<X> success
                          //todo: wrap apply() in a try/catch?
                          ? bind.f().apply(success.value())
                          : result(((Unsuccessful<X>) resX).contort()),
                          ex, canceller, continuation, sd + 1),
             stackDepth + 1);
    }

    private <X, A> void parallel(Parallel<X, A> parallel, Executor executor, Canceller canceller,
                                 Continuation<A> continuation) {
        List<Fiber<X>>                         fibers    = parallel.fibers();
        Function<? super List<X>, ? extends A> f         = parallel.f();
        int                                    n         = fibers.size();
        Object[]                               results   = new Object[n];
        AtomicInteger                          remaining = new AtomicInteger(n);

        Canceller child = canceller.addChild();
        for (int i = 0; i < n; i++) {
            final int finalI = i;
            preempt(fibers.get(finalI), executor, child, (sd, ex, result) -> {
                if (result instanceof Success<X> success) {
                    results[finalI] = success.value();
                    if (remaining.decrementAndGet() == 0) {
                        //todo: replace List with something with an immutable interface
                        @SuppressWarnings("unchecked") List<X> list = (List<X>) asList(results);
                        tick(succeeded(f.apply(list)), ex, canceller, continuation, sd + 1);
                    }
                } else if (remaining.getAndSet(-1) > 0) {
                    child.cancel();
                    tick(result(result instanceof Failure<X> failure
                                ? failure.contort()
                                : cancellation()),
                         ex, canceller, continuation, sd + 1);
                }
            });
        }
    }

    public static FiberRunLoop fiberRunLoop(Environment environment, RuntimeSettings runtimeSettings) {
        return new FiberRunLoop(environment.cancellerFactory(), environment.defaultExecutor(),
                                environment.timer(), runtimeSettings.maxTicksBeforePreemption());
    }

    public static FiberRunLoop system() {
        return System.LOADED;
    }

    private interface Continuation<A> {
        void accept(Integer stackDepth, Executor executor, Result<A> result);

        static <A> Continuation<A> root(Consumer<? super Result<A>> k) {
            return (__, ___, res) -> k.accept(res);
        }
    }

    private static final class System {
        private static final FiberRunLoop LOADED = fiberRunLoop(Environment.system(), RuntimeSettings.system());
    }
}
