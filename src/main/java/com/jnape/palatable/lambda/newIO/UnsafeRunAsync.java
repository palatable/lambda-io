package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Id.id;
import static com.jnape.palatable.lambda.newIO.IO.io;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.ForkJoinPool.commonPool;

public final class UnsafeRunAsync<A> implements Interpreter<A, CompletableFuture<A>> {

    private final Executor executor;

    private UnsafeRunAsync(Executor executor) {
        this.executor = executor;
    }

    @Override
    public CompletableFuture<A> interpret(A a) {
        return completedFuture(a);
    }

    @Override
    public CompletableFuture<A> interpret(Fn0<A> thunk) {
        return supplyAsync(thunk.toSupplier(), executor);
    }

    @Override
    public CompletableFuture<A> interpret(Callback<? super Callback<? super A>> k) {
        return new CompletableFuture<A>() {{
            k.apply((Callback<A>) this::complete);
        }};
    }

    @Override
    public <Z> CompletableFuture<A> interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
        return new CompletableFuture<A>() {{
            ioZ.interpret(new Phi<>(this, ioF, executor));
        }};
    }

    @Override
    public <Z> CompletableFuture<A> interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
        return new CompletableFuture<A>() {{
            ioZ.interpret(new Psi<>(this, f, executor));
        }};
    }

    public static <A> UnsafeRunAsync<A> unsafeRunAsync(Executor executor) {
        return new UnsafeRunAsync<>(executor);
    }

    private static final class Mu<Z, A> implements Interpreter<Z, Unit> {
        private final CompletableFuture<A>        future;
        private final Fn1<? super Z, ? extends A> f;
        private final Executor                    executor;

        private Mu(CompletableFuture<A> future, Fn1<? super Z, ? extends A> f, Executor executor) {
            this.future   = future;
            this.f        = f;
            this.executor = executor;
        }

        @Override
        public Unit interpret(Z z) {
            executor.execute(() -> future.complete(f.apply(z)));
            return UNIT;
        }

        @Override
        public Unit interpret(Fn0<Z> thunk) {
            executor.execute(() -> {
                Z apply = thunk.apply();
                executor.execute(() -> {
                    A a = f.apply(apply);
                    future.complete(a);
                });
            });
            return UNIT;
        }

        @Override
        public Unit interpret(Callback<? super Callback<? super Z>> k) {
            executor.execute(() -> k.apply((Callback<Z>) z -> executor.execute(() -> future.complete(f.apply(z)))));
            return UNIT;
        }

        @Override
        public <Y> Unit interpret(IO<Y> ioY, IO<Fn1<? super Y, ? extends Z>> ioG) {
            return ioY.interpret(new Phi<>(future, ioG.bind(yz -> io(y -> f.apply(yz.apply(y)))), executor));
        }

        @Override
        public <Y> Unit interpret(IO<Y> ioY, Fn1<? super Y, ? extends IO<Z>> g) {
            return ioY.interpret(new Psi<>(future, y -> g.apply(y).bind(z -> io(f.apply(z))), executor));
        }
    }

    private static class Phi<Z, A> implements Interpreter<Z, Unit> {
        private final CompletableFuture<A>            future;
        private final IO<Fn1<? super Z, ? extends A>> ioF;
        private final Executor                        executor;

        private Phi(CompletableFuture<A> future, IO<Fn1<? super Z, ? extends A>> ioF, Executor executor) {
            this.future   = future;
            this.ioF      = ioF;
            this.executor = executor;
        }

        @Override
        public Unit interpret(Z z) {
            ioF.interpret(new Mu<>(future, za -> za.apply(z), executor));
            return UNIT;
        }

        @Override
        public Unit interpret(Fn0<Z> thunk) {
            CompletableFuture<Z> futureZ = supplyAsync(thunk.toSupplier(), executor);
            return ioF.interpret(new Mu<>(future, za -> za.apply(futureZ.get()), executor));
        }

        @Override
        public Unit interpret(Callback<? super Callback<? super Z>> k) {
            CompletableFuture<Z> futureZ = new CompletableFuture<Z>() {{
                k.apply((Callback<Z>) this::complete);
            }};
            return ioF.interpret(new Mu<>(future, za -> za.apply(futureZ.get()), executor));
        }

        @Override
        public <Y> Unit interpret(IO<Y> ioY, IO<Fn1<? super Y, ? extends Z>> ioG) {
            return ioY.interpret(new Phi<>(future, ioG.ap(ioF.bind(za -> io(yz -> yz.fmap(za)))), executor));
        }

        @Override
        public <Y> Unit interpret(IO<Y> ioY, Fn1<? super Y, ? extends IO<Z>> g) {
            return ioY.interpret(new Nu<>(future, g, ioF, executor));
        }
    }

    private static class Psi<Z, A> implements Interpreter<Z, Unit> {
        private final CompletableFuture<A>            future;
        private final Fn1<? super Z, ? extends IO<A>> f;
        private final Executor                        executor;

        private Psi(CompletableFuture<A> future,
                    Fn1<? super Z, ? extends IO<A>> f, Executor executor) {
            this.future   = future;
            this.f        = f;
            this.executor = executor;
        }

        @Override
        public Unit interpret(Z z) {
            return f.apply(z).interpret(new Mu<>(future, id(), executor));
        }

        @Override
        public Unit interpret(Fn0<Z> thunk) {
            executor.execute(() -> {
                Z z = thunk.apply();
                f.apply(z).interpret(new Mu<>(future, id(), executor));
            });
            return UNIT;
        }

        @Override
        public Unit interpret(Callback<? super Callback<? super Z>> k) {
            executor.execute(() -> k.apply((Callback<Z>) z -> f.apply(z).interpret(new Mu<>(future, id(), executor))));
            return UNIT;
        }

        @Override
        public <Y> Unit interpret(IO<Y> ioY, IO<Fn1<? super Y, ? extends Z>> ioG) {
            return ioY.interpret(new Nu2<>(future, ioG, f, executor));
        }

        @Override
        public <Y> Unit interpret(IO<Y> ioZ, Fn1<? super Y, ? extends IO<Z>> g) {
            return ioZ.interpret(new Psi<>(future, y -> g.apply(y).bind(f), executor));
        }
    }

    private static final class Nu<Y, Z, A> implements Interpreter<Y, Unit> {
        private final CompletableFuture<A>            future;
        private final Fn1<? super Y, ? extends IO<Z>> f;
        private final IO<Fn1<? super Z, ? extends A>> ioG;
        private final Executor                        executor;

        private Nu(CompletableFuture<A> future,
                   Fn1<? super Y, ? extends IO<Z>> f,
                   IO<Fn1<? super Z, ? extends A>> ioG, Executor executor) {
            this.future   = future;
            this.f        = f;
            this.ioG      = ioG;
            this.executor = executor;
        }

        @Override
        public Unit interpret(Y y) {
            return f.apply(y).interpret(new Phi<>(future, ioG, executor));
        }

        @Override
        public Unit interpret(Fn0<Y> thunk) {
            executor.execute(() -> {
                Y y = thunk.apply();
                f.apply(y).interpret(new Phi<>(future, ioG, executor));
            });
            return UNIT;
        }

        @Override
        public Unit interpret(Callback<? super Callback<? super Y>> k) {
            executor.execute(() -> k.apply((Callback<Y>) y -> f.apply(y).interpret(new Phi<>(future, ioG, executor))));
            return UNIT;
        }

        @Override
        public <X> Unit interpret(IO<X> ioX, IO<Fn1<? super X, ? extends Y>> ioH) {
            return ioX.interpret(new Nu2<>(future, ioH, y -> f.apply(y).ap(ioG), executor));
        }

        @Override
        public <X> Unit interpret(IO<X> ioX, Fn1<? super X, ? extends IO<Y>> h) {
            return ioX.interpret(new Nu<>(future, x -> h.apply(x).bind(f), ioG, executor));
        }
    }

    private static final class Nu2<Y, Z, A> implements Interpreter<Y, Unit> {
        private final CompletableFuture<A>            future;
        private final IO<Fn1<? super Y, ? extends Z>> ioG;
        private final Fn1<? super Z, ? extends IO<A>> f;
        private final Executor                        executor;

        private Nu2(CompletableFuture<A> future,
                    IO<Fn1<? super Y, ? extends Z>> ioG,
                    Fn1<? super Z, ? extends IO<A>> f, Executor executor) {
            this.future   = future;
            this.ioG      = ioG;
            this.f        = f;
            this.executor = executor;
        }

        @Override
        public Unit interpret(Y y) {
            return ioG.interpret(new Psi<>(future, yz -> f.apply(yz.apply(y)), executor));
        }

        @Override
        public Unit interpret(Fn0<Y> thunk) {
            executor.execute(() -> {
                Y y = thunk.apply(); // sleeps here
                ioG.interpret(new Psi<>(future, yz -> f.apply(yz.apply(y)), executor));
            });
            return UNIT;
        }

        @Override
        public Unit interpret(Callback<? super Callback<? super Y>> k) {
            executor.execute(() -> k.apply((Callback<Y>) y -> ioG
                    .interpret(new Psi<>(future, yz -> f.apply(yz.apply(y)), executor))));
            return UNIT;
        }

        @Override
        public <X> Unit interpret(IO<X> ioX, IO<Fn1<? super X, ? extends Y>> ioH) {
            return ioX.interpret(
                    new Nu2<>(future, ioH.<Fn1<? super X, ? extends Z>>ap(ioG.bind(yz -> io(xy -> xy.fmap(yz)))),
                              f, executor));
        }

        @Override
        public <X> Unit interpret(IO<X> ioX, Fn1<? super X, ? extends IO<Y>> h) {
            return ioX.interpret(new Psi<>(future, x -> h.apply(x).ap(ioG).bind(f), executor));
        }
    }

    public static void main2(String[] args) {
        IO<Integer> par = io(() -> {
            threadLog("one");
            Thread.sleep(1000);
            return 0;
        }).<Integer>ap(io(() -> {
            threadLog("two");
            Thread.sleep(1000);
            return x -> x + 1;
        }));
        IO<Integer> seq = par.bind(x -> io(() -> {
            threadLog("three");
            Thread.sleep(1000);
            return x + 1;
        }));
        seq.unsafePerformAsyncIO().join();
    }

    public static void main(String[] args) {
        IO.async(() -> {
                    threadLog("one");
                    Thread.sleep(1000);
                    return 0;
                }, commonPool())
                .<Integer>ap(IO.async(() -> {
                                 threadLog("two");
                                 Thread.sleep(1000);
                                 return x -> x + 1;
                             }, commonPool())
                ).bind(x -> io(() -> {
                    threadLog("three");
                    return x + 1;
                })).unsafePerformAsyncIO()
                .join();
    }

    private static void threadLog(Object message) {
        System.out.println(Thread.currentThread() + ": " + message);
    }
}
