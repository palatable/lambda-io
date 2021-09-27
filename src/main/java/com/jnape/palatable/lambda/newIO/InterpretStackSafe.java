package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.adt.Try;
import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.util.concurrent.CompletableFuture;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.adt.Try.failure;
import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;
import static com.jnape.palatable.lambda.newIO.Callback.callback;
import static com.jnape.palatable.lambda.newIO.IO.io;

public final class InterpretStackSafe<A, R> implements Interpreter<A, R> {

    public interface QuantifiedF<A, R> {
        R universal(A a);

        <X> Either<X, R> existential(X x);
    }

    public interface QuantifiedG<A, R> {
        R universal(Fn0<A> f);

        <X> Either<X, R> existential(Fn0<X> f);
    }

    public interface QuantifiedH<A, R> {
        R universal(Callback<? super Callback<? super A>> k);

        <X> Either<X, R> existential(Callback<? super Callback<? super X>> k);
    }

    private final QuantifiedF<A, R> f;
    private final QuantifiedG<A, R> g;
    private final QuantifiedH<A, R> h;

    private InterpretStackSafe(QuantifiedF<A, R> f,
                               QuantifiedG<A, R> g,
                               QuantifiedH<A, R> h) {
        this.f = f;
        this.g = g;
        this.h = h;
    }

    @Override
    public R interpret(A a) {
        return f.universal(a);
    }

    @Override
    public R interpret(Fn0<A> thunk) {
        return g.universal(thunk);
    }

    @Override
    public R interpret(Callback<? super Callback<? super A>> k) {
        return h.universal(k);
    }

    @Override
    public <Z> R interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
        return runEverything(ioZ.bind(z -> ioF.bind(za -> io(za.apply(z)))), this.f, g, h);
    }

    @Override
    public <Z> R interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
        return runEverything(ioZ.bind(f), this.f, g, h);
    }

    private static <A, R> R runEverything(IO<A> io,
                                          QuantifiedF<A, R> f,
                                          QuantifiedG<A, R> g,
                                          QuantifiedH<A, R> h) {
        Interpreter<A, RecursiveResult<IO<A>, R>> interpreter = new Interpreter<>() {
            @Override
            public RecursiveResult<IO<A>, R> interpret(A a) {
                return terminate(f.universal(a));
            }

            @Override
            public RecursiveResult<IO<A>, R> interpret(Fn0<A> thunk) {
                return terminate(g.universal(thunk));
            }

            @Override
            public RecursiveResult<IO<A>, R> interpret(Callback<? super Callback<? super A>> k) {
                return terminate(h.universal(k));
            }

            @Override
            public <Z> RecursiveResult<IO<A>, R> interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioZA) {
                return ioZ.interpret(new Interpreter<>() {
                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Z z) {
                        return f.existential(z).match(z_ -> recurse(ioZA.bind(za -> io(za.apply(z_)))),
                                                      RecursiveResult::terminate);
                    }

                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Fn0<Z> thunk) {
                        return g.existential(thunk).match(z_ -> recurse(ioZA.bind(za -> io(za.apply(z_)))),
                                                          RecursiveResult::terminate);
                    }

                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Callback<? super Callback<? super Z>> k) {
                        return h.existential(k).match(z_ -> recurse(ioZA.bind(za -> io(za.apply(z_)))),
                                                      RecursiveResult::terminate);
                    }

                    @Override
                    public <Y> RecursiveResult<IO<A>, R> interpret(IO<Y> ioY, IO<Fn1<? super Y, ? extends Z>> ioYZ) {
                        return recurse(ioY.bind(y -> ioYZ.bind(yz -> ioZA.bind(za -> io(za.apply(yz.apply(y)))))));
                    }

                    @Override
                    public <Y> RecursiveResult<IO<A>, R> interpret(IO<Y> ioY, Fn1<? super Y, ? extends IO<Z>> g) {
                        return recurse(ioY.bind(y -> g.apply(y).bind(z -> ioZA.bind(za -> io(za.apply(z))))));
                    }
                });
            }

            @Override
            public <Z> RecursiveResult<IO<A>, R> interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> xx) {
                return ioZ.interpret(new Interpreter<>() {
                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Z z) {
                        return f.existential(z).match(z_ -> recurse(xx.apply(z_)),
                                                      RecursiveResult::terminate);
                    }

                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Fn0<Z> thunk) {
                        return g.existential(thunk).match(z_ -> recurse(xx.apply(z_)),
                                                          RecursiveResult::terminate);
                    }

                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Callback<? super Callback<? super Z>> k) {
                        return h.existential(k).match(z_ -> recurse(xx.apply(z_)),
                                                      RecursiveResult::terminate);
                    }

                    @Override
                    public <Y> RecursiveResult<IO<A>, R> interpret(IO<Y> ioY, IO<Fn1<? super Y, ? extends Z>> ioYZ) {
                        return recurse(ioY.bind(y -> ioYZ.bind(yz -> xx.apply(yz.apply(y)))));
                    }

                    @Override
                    public <Y> RecursiveResult<IO<A>, R> interpret(IO<Y> ioY, Fn1<? super Y, ? extends IO<Z>> xxx) {
                        return recurse(ioY.bind(y -> xxx.apply(y).bind(xx)));
                    }
                });
            }
        };
        return trampoline(io_ -> io_.interpret(interpreter), io);
    }

    public static <A> InterpretStackSafe<A, Try<A>> trying() {
        return new InterpretStackSafe<>(
                new QuantifiedF<>() {
                    @Override
                    public Try<A> universal(A a) {
                        return Try.success(a);
                    }

                    @Override
                    public <X> Either<X, Try<A>> existential(X x) {
                        return left(x);
                    }
                },
                new QuantifiedG<>() {
                    @Override
                    public Try<A> universal(Fn0<A> f) {
                        return Try.trying(f);
                    }

                    @Override
                    public <X> Either<X, Try<A>> existential(Fn0<X> f) {
                        return Try.trying(f).match(t -> right(failure(t)), Either::left);
                    }
                },
                new QuantifiedH<>() {
                    @Override
                    public Try<A> universal(Callback<? super Callback<? super A>> k) {
                        return Try.trying(() -> await(k));
                    }

                    @Override
                    public <X> Either<X, Try<A>> existential(Callback<? super Callback<? super X>> k) {
                        return Try.trying(() -> await(k))
                                .match(t -> right(failure(t)), Either::left);
                    }

                    private static <A> A await(Callback<? super Callback<? super A>> k) {
                        return new CompletableFuture<A>() {{
                            k.apply(callback(this::complete));
                        }}.join();
                    }
                });
    }

    public static void main(String[] args) {

        class Slot {
            int x = 0;
            final long start = System.currentTimeMillis();
        }
        Slot slot = new Slot();

        System.out.println(forever(IO.<Unit>async(k -> {
            int x = slot.x++;

            if (x % 10_000_000 == 0) {
                long totalMs = System.currentTimeMillis() - slot.start;
                System.out.println(x + " (avg " + (x / totalMs) + "/ms)");
            }
            if (x == 1_000_000_000)
                throw new IllegalStateException("so close!");

            k.call(UNIT);
        })).interpret(trying()));
    }

    public static <A, B> IO<B> forever(IO<A> io) {
        return io.bind(__ -> forever(io));
    }
}
