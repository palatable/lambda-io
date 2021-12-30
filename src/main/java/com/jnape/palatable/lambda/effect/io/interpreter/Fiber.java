package com.jnape.palatable.lambda.effect.io.interpreter;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.effect.io.IO;
import com.jnape.palatable.lambda.effect.io.Interpreter;
import com.jnape.palatable.lambda.effect.io.fiber.Cancel;
import com.jnape.palatable.lambda.effect.io.fiber.FiberCallback;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.Cancelled;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.Failure;
import com.jnape.palatable.lambda.effect.io.fiber.FiberResult.Success;
import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import com.jnape.palatable.lambda.functions.Fn1;

import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.effect.io.IO.io;
import static com.jnape.palatable.lambda.effect.io.fiber.FiberResult.cancelled;
import static com.jnape.palatable.lambda.effect.io.fiber.FiberResult.success;

public final class Fiber<A> implements Interpreter<A, Unit> {
    private final int              currentWorkCount;
    private final int              workUnitsPerQuanta;
    private final Scheduler        scheduler;
    private final Cancel           cancel;
    private final FiberCallback<A> callback;

    public Fiber(int currentWorkCount, int stackDepthShiftCoefficient, Scheduler scheduler,
                 Cancel cancel, FiberCallback<A> callback) {
        this.currentWorkCount   = currentWorkCount;
        this.workUnitsPerQuanta = stackDepthShiftCoefficient;
        this.scheduler          = scheduler;
        this.cancel             = cancel;
        this.callback           = callback;
    }

    @Override
    public Unit interpret(A a) {
        callback.call(success(a));
        return UNIT;
    }

    @Override
    public Unit interpret(Consumer<? super Consumer<? super FiberResult<A>>> k) {
        if (cancel.cancelled())
            callback.call(cancelled());
        else
            k.accept((Consumer<? super FiberResult<A>>) callback::call);
        return UNIT;
    }

    @Override
    public <Z> Unit interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
        return interpret(ioZ, (Fn1<Z, IO<A>>) z -> ioF.bind(za -> io(za.apply(z))));
    }

    private sealed interface Res<A> {
    }

    private record Bind<Z, A>(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) implements Res<A> {
        interface Eliminator<A> {
            <Z> void apply(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f);

        }

        public void eliminate(Eliminator<A> eliminator) {
            eliminator.apply(ioZ, f);
        }
    }

    private record NonBind<A>(IO<A> ioA) implements Res<A> {
    }

    private static final class Specialized<A> implements Interpreter<A, Res<A>> {

        public static final Specialized<?> INSTANCE = new Specialized<>();

        public <X> Res<X> interpret(IO<X> io) {
            @SuppressWarnings("unchecked") Specialized<X> s = (Specialized<X>) this;
            return io.interpret(s);
        }

        @Override
        public Res<A> interpret(A a) {
            return new NonBind<>(io(a));
        }

        @Override
        public Res<A> interpret(Consumer<? super Consumer<? super FiberResult<A>>> k) {
            return new NonBind<>(io(k));
        }

        @Override
        public <Z> Res<A> interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
            return new Bind<>(ioZ, z -> ioF.bind(za -> io(za.apply(z))));
        }

        @Override
        public <Z> Res<A> interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
            return new Bind<>(ioZ, f);
        }
    }

    private static <Z, A> void tick(Bind<Z, A> bind, Scheduler scheduler, FiberCallback<A> callback,
                                    Cancel cancel, int stackDepth, int maxDepth) {
        if (cancel.cancelled()) {
            callback.call(cancelled());
            return;
        }

        if (Specialized.INSTANCE.interpret(bind.ioZ()) instanceof Bind<?, Z> bindZ) {
            bindZ.eliminate(new Bind.Eliminator<Z>() {
                @Override
                public <Y> void apply(IO<Y> ioY, Fn1<? super Y, ? extends IO<Z>> g) {
                    if (stackDepth == maxDepth)
                        scheduler.schedule(() -> tick(new Bind<>(ioY, y -> g.apply(y).bind(bind.f())), scheduler, callback, cancel, 1, maxDepth));
                    else
                        tick(new Bind<>(ioY, y -> g.apply(y).bind(bind.f())), scheduler, callback, cancel, stackDepth + 1, maxDepth);
                }
            });
        } else {
            bind.ioZ().interpret(new Interpreter<Z, Unit>() {
                @Override
                public Unit interpret(Z z) {
                    if (stackDepth == maxDepth)
                        scheduler.schedule(() -> bind.f.apply(z).interpret(new Fiber<>(1, maxDepth, scheduler, cancel, callback)));
                    else
                        bind.f.apply(z).interpret(new Fiber<>(stackDepth + 1, maxDepth, scheduler, cancel, callback));
                    return UNIT;
                }

                @Override
                public Unit interpret(Consumer<? super Consumer<? super FiberResult<Z>>> k) {
                    if (cancel.cancelled())
                        callback.call(cancelled());
                    else
                        k.accept((Consumer<? super FiberResult<Z>>) resultZ -> {
                            if (resultZ instanceof Cancelled<Z> cancelledZ) {
                                callback.call(cancelledZ.contort());
                            } else if (resultZ instanceof Success<Z> successZ) {
                                //todo: catch around f everywhere
                                IO<A> next = bind.f.apply(successZ.result());
                                if (stackDepth == maxDepth)
                                    scheduler.schedule(() -> next.interpret(new Fiber<>(1, maxDepth, scheduler, cancel, callback)));
                                else
                                    next.interpret(new Fiber<>(stackDepth + 1, maxDepth, scheduler, cancel, callback));
                            } else {
                                callback.call(((Failure<Z>) resultZ).contort());
                            }
                        });
                    return UNIT;
                }

                @Override
                public <Y> Unit interpret(IO<Y> ioY, IO<Fn1<? super Y, ? extends Z>> ioF) {
                    return interpret(ioY, (Fn1<? super Y, ? extends IO<Z>>) y -> ioF.bind(yz -> io(yz.apply(y))));
                }

                @Override
                public <Y> Unit interpret(IO<Y> ioY, Fn1<? super Y, ? extends IO<Z>> g) {
                    IO<A> next = ioY.bind(y -> g.apply(y).bind(bind.f));
                    if (stackDepth == maxDepth) {
                        scheduler.schedule(() -> next.interpret(new Fiber<>(1, maxDepth, scheduler, cancel, callback)));
                    } else {
                        next.interpret(new Fiber<>(stackDepth + 1, maxDepth, scheduler, cancel, callback));
                    }
                    return UNIT;
                }
            });
        }
    }

    @Override
    public <Z> Unit interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
        tick(new Bind<>(ioZ, f), scheduler, callback, cancel, currentWorkCount, workUnitsPerQuanta);
        return UNIT;
    }
}
