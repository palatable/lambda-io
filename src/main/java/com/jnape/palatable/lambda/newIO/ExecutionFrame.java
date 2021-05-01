package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jnape.palatable.lambda.adt.Maybe.maybe;
import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.functions.Fn0.fn0;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.recurse;
import static com.jnape.palatable.lambda.functions.recursion.RecursiveResult.terminate;
import static com.jnape.palatable.lambda.newIO.ExecutionSpine.asyncSpine;
import static com.jnape.palatable.lambda.newIO.ExecutionSpine.syncSpine;
import static java.util.concurrent.ForkJoinPool.commonPool;

public interface ExecutionFrame<Context> {
    RecursiveResult<ExecutionSpine, Unit> pop(Context context);

    interface AsyncFrame<A> extends ExecutionFrame<ExecutionFrame<A>> {

        static <A> ExecutionFrame<ExecutionFrame<A>> asyncFrame(Fn1<Fn1<? super A, ? extends Unit>, Unit> k) {
            return syncFrame -> {
                // todo: disaster
                AtomicBoolean                     isAsync = new AtomicBoolean();
                CompletableFuture<ExecutionSpine> appRef  = new CompletableFuture<>();
                k.apply(a -> {
                    if (isAsync.get()) {
                        appRef.complete(null);
                        return syncSpine(syncFrame, a).collapse();
                    }

                    appRef.complete(syncSpine(syncFrame, a));
                    return UNIT;
                });
                isAsync.set(true);

                return maybe(appRef.join())
                        .match(fn0(() -> terminate(UNIT)),
                               RecursiveResult::recurse);
            };
        }

        static <A> ExecutionFrame<ExecutionFrame<A>> fork(Fn0<A> fn0, Executor executor) {
            return asyncFrame(f -> {
                executor.execute(() -> f.apply(fn0.apply()));
                return UNIT;
            });
        }

        static <A> ExecutionFrame<ExecutionFrame<A>> value(A a) {
            return syncFrame -> syncFrame.pop(a);
        }

        static <Z, A> ExecutionFrame<ExecutionFrame<A>> map(Fn1<? super Z, ? extends A> fn,
                                                            ExecutionFrame<ExecutionFrame<Z>> asyncFrame) {
            return syncFrame -> recurse(asyncSpine(asyncFrame, SyncFrame.map(fn, syncFrame)));
        }

        static <Z, A> ExecutionFrame<ExecutionFrame<A>> zip(
                ExecutionFrame<ExecutionFrame<Fn1<? super Z, ? extends A>>> asyncFrameF,
                ExecutionFrame<ExecutionFrame<Z>> asyncFrame) {
            return syncFrame -> recurse(asyncSpine(asyncFrameF, SyncFrame.zip(asyncFrame, syncFrame)));
        }

        static <Z, A> ExecutionFrame<ExecutionFrame<A>> flatMap(ExecutionFrame<ExecutionFrame<Z>> asyncFrame,
                                                                Fn1<? super Z, ? extends ExecutionFrame<ExecutionFrame<A>>> fn) {
            return syncFrame -> recurse(asyncSpine(asyncFrame, SyncFrame.flatMap(fn, syncFrame)));
        }
    }

    interface SyncFrame<A> extends ExecutionFrame<A> {

        static <A> ExecutionFrame<A> terminalFrame(Fn1<? super A, ? extends Unit> k) {
            return a -> terminate(k.apply(a));
        }

        static <A, B> ExecutionFrame<A> map(Fn1<? super A, ? extends B> fn, ExecutionFrame<B> syncFrame) {
            return a -> recurse(syncSpine(syncFrame, fn.apply(a)));
        }

        static <A, B> ExecutionFrame<Fn1<? super A, ? extends B>> zip(ExecutionFrame<ExecutionFrame<A>> asyncFrame,
                                                                      ExecutionFrame<B> syncFrame) {
            return f -> recurse(asyncSpine(asyncFrame, map(f, syncFrame)));
        }

        static <A, B> ExecutionFrame<A> flatMap(Fn1<? super A, ? extends ExecutionFrame<ExecutionFrame<B>>> fn,
                                                ExecutionFrame<B> syncFrame) {
            return a -> recurse(asyncSpine(fn.apply(a), syncFrame));
        }
    }

    static void main(String[] args) {
        int          n  = 20_000_000;
        ForkJoinPool ex = commonPool();
        ExecutionFrame<ExecutionFrame<Integer>> deeplyLeftAssociated = times(
                n,
                f -> AsyncFrame.map(x -> x + 1, f),
                AsyncFrame.asyncFrame(k -> {
                    ex.execute(() -> {
                        System.out.println("Running...");
                        k.apply(42);
                    });
                    return UNIT;
                }));

        int i = 0;
        while (i++ < 10) {
            new CompletableFuture<Integer>() {{
                ExecutionSpine application = asyncSpine(deeplyLeftAssociated, SyncFrame.terminalFrame(x -> {
                    complete(x);
                    return UNIT;
                }));
                long startMs = System.currentTimeMillis();
                application.collapse();
                System.out.println(join());
                System.out.println(n / (System.currentTimeMillis() - startMs) + "/ms");
            }};
        }
    }
}
