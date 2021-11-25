package com.jnape.palatable.lambda.effect.io.interpreter;

import com.jnape.palatable.lambda.adt.Either;
import com.jnape.palatable.lambda.adt.Try;
import com.jnape.palatable.lambda.effect.io.Callback;
import com.jnape.palatable.lambda.effect.io.IO;
import com.jnape.palatable.lambda.effect.io.Interpreter;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.util.concurrent.CompletableFuture;

import static com.jnape.palatable.lambda.adt.Either.left;
import static com.jnape.palatable.lambda.adt.Either.right;
import static com.jnape.palatable.lambda.adt.Try.failure;
import static com.jnape.palatable.lambda.effect.io.Callback.callback;
import static com.jnape.palatable.lambda.effect.io.IO.io;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;

public final class RewritingInterpreter<A, R> implements Interpreter<A, R> {

    private final LeafInterpreter<A, R> leafInterpreter;

    private RewritingInterpreter(LeafInterpreter<A, R> leafInterpreter) {
        this.leafInterpreter = leafInterpreter;
    }

    public static <A, R> RewritingInterpreter<A, R> rewritingInterpreter(LeafInterpreter<A, R> leafInterpreter) {
        return new RewritingInterpreter<>(leafInterpreter);
    }

    @Override
    public R interpret(A a) {
        return leafInterpreter.interpret(a);
    }

    @Override
    public R interpret(Fn0<? extends A> thunk) {
        return leafInterpreter.interpret(thunk);
    }

    @Override
    public R interpret(Callback<? super Callback<? super A>> k) {
        return leafInterpreter.interpret(k);
    }

    @Override
    public <Z> R interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
        return runEverything(ioZ.bind(z -> ioF.bind(za -> io(za.apply(z)))), leafInterpreter);
    }

    @Override
    public <Z> R interpret(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
        return runEverything(ioZ.bind(f), leafInterpreter);
    }

    private static <A, R> R runEverything(IO<A> io, LeafInterpreter<A, R> leafInterpreter) {
        Interpreter<A, RecursiveResult<IO<A>, R>> interpreter = new Interpreter<>() {
            @Override
            public RecursiveResult<IO<A>, R> interpret(A a) {
                return terminate(leafInterpreter.interpret(a));
            }

            @Override
            public RecursiveResult<IO<A>, R> interpret(Fn0<? extends A> thunk) {
                return terminate(leafInterpreter.interpret(thunk));
            }

            @Override
            public RecursiveResult<IO<A>, R> interpret(Callback<? super Callback<? super A>> k) {
                return terminate(leafInterpreter.interpret(k));
            }

            @Override
            public <Z> RecursiveResult<IO<A>, R> interpret(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioZA) {
                return ioZ.interpret(new Interpreter<>() {
                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Z z) {
                        return leafInterpreter.interpretE(z).match(z_ -> recurse(ioZA.bind(za -> io(za.apply(z_)))),
                                                                   RecursiveResult::terminate);
                    }

                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Fn0<? extends Z> thunk) {
                        return leafInterpreter.interpretE(thunk).match(z_ -> recurse(ioZA.bind(za -> io(za.apply(z_)))),
                                                                       RecursiveResult::terminate);
                    }

                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Callback<? super Callback<? super Z>> k) {
                        return leafInterpreter.interpretE(k).match(z_ -> recurse(ioZA.bind(za -> io(za.apply(z_)))),
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
                        return leafInterpreter.interpretE(z).match(z_ -> recurse(xx.apply(z_)),
                                                                   RecursiveResult::terminate);
                    }

                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Fn0<? extends Z> thunk) {
                        return leafInterpreter.interpretE(thunk).match(z_ -> recurse(xx.apply(z_)),
                                                                       RecursiveResult::terminate);
                    }

                    @Override
                    public RecursiveResult<IO<A>, R> interpret(Callback<? super Callback<? super Z>> k) {
                        return leafInterpreter.interpretE(k).match(z_ -> recurse(xx.apply(z_)),
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

    public static <A> RewritingInterpreter<A, Try<A>> trying() {
        return new RewritingInterpreter<>(new LeafInterpreter<>() {
            @Override
            public Try<A> interpret(A a) {
                return Try.success(a);
            }

            @Override
            public <X> Either<X, Try<A>> interpretE(X x) {
                return left(x);
            }

            @Override
            public Try<A> interpret(Fn0<? extends A> thunk) {
                return Try.trying(thunk);
            }

            @Override
            public <X> Either<X, Try<A>> interpretE(Fn0<? extends X> thunk) {
                return Try.<X>trying(thunk)
                        .match(t -> right(failure(t)),
                               Either::left);
            }

            @Override
            public Try<A> interpret(Callback<? super Callback<? super A>> k) {
                return Try.trying(() -> await(k));
            }

            @Override
            public <X> Either<X, Try<A>> interpretE(Callback<? super Callback<? super X>> k) {
                return Try.<X>trying(() -> await(k))
                        .match(t -> right(failure(t)),
                               Either::left);
            }
        });
    }

    public sealed interface ShiftOrNext<A> {

    }

    public record Shift<A>(IO<A> io, int steps) implements ShiftOrNext<A> {
    }

    public record Next<A>(IO<A> io, int current) implements ShiftOrNext<A> {

    }

    public static <A> RewritingInterpreter<A, A> runSync() {
        return rewritingInterpreter(new LeafInterpreter<A, A>() {
            @Override
            public A interpret(A a) {
                return a;
            }

            @Override
            public <X> Either<X, A> interpretE(X x) {
                return left(x);
            }

            @Override
            public A interpret(Fn0<? extends A> thunk) {
                return thunk.apply();
            }

            @Override
            public <X> Either<X, A> interpretE(Fn0<? extends X> thunk) {
                return left(thunk.apply());
            }

            @Override
            public A interpret(Callback<? super Callback<? super A>> k) {
                return await(k);
            }

            @Override
            public <X> Either<X, A> interpretE(Callback<? super Callback<? super X>> k) {
                return left(await(k));
            }
        });
    }

    private static <A> A await(Callback<? super Callback<? super A>> k) {
        return new CompletableFuture<A>() {{
            k.apply(callback(this::complete));
        }}.join();
    }
}
