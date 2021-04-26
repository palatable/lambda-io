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
import static com.jnape.palatable.lambda.newIO.ExecutionFrame.SyncFrame.syncFlatMap;
import static com.jnape.palatable.lambda.newIO.ExecutionFrame.SyncFrame.syncZip;
import static com.jnape.palatable.lambda.newIO.ExecutionSpine.asyncSpine;
import static com.jnape.palatable.lambda.newIO.ExecutionSpine.syncSpine;
import static java.util.concurrent.ForkJoinPool.commonPool;

public interface ExecutionFrame<Context> {
    RecursiveResult<ExecutionSpine, Unit> pop(Context context);

    interface AsyncFrame<A> extends ExecutionFrame<SyncFrame<A>> {

        static <A> AsyncFrame<A> asyncFrame(Fn1<Fn1<? super A, ? extends Unit>, Unit> k) {
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

        static <A> AsyncFrame<A> fork(Fn0<A> fn0, Executor executor) {
            return asyncFrame(f -> {
                executor.execute(() -> f.apply(fn0.apply()));
                return UNIT;
            });
        }

        static <A> AsyncFrame<A> value(A a) {
            return syncFrame -> syncFrame.pop(a);
        }

        static <Z, A> AsyncFrame<A> map(Fn1<? super Z, ? extends A> fn, AsyncFrame<Z> asyncFrame) {
            return syncFrame -> recurse(asyncSpine(asyncFrame, SyncFrame.map(fn, syncFrame)));
        }

        static <Z, A> AsyncFrame<A> zip(AsyncFrame<Fn1<? super Z, ? extends A>> asyncFrameF,
                                        AsyncFrame<Z> asyncFrame) {
            return syncFrame -> recurse(asyncSpine(asyncFrameF, syncZip(asyncFrame, syncFrame)));
        }

        static <Z, A> AsyncFrame<A> flatMap(AsyncFrame<Z> asyncFrame, Fn1<? super Z, ? extends AsyncFrame<A>> fn) {
            return syncFrame -> recurse(asyncSpine(asyncFrame, syncFlatMap(fn, syncFrame)));
        }
    }

    interface SyncFrame<A> extends ExecutionFrame<A> {

        static <A> SyncFrame<A> syncFrame(Fn1<? super A, ? extends Unit> k) {
            return a -> terminate(k.apply(a));
        }

        static <A, B> SyncFrame<A> map(Fn1<? super A, ? extends B> fn, SyncFrame<B> syncFrame) {
            return a -> recurse(syncSpine(syncFrame, fn.apply(a)));
        }

        static <A, B> SyncFrame<Fn1<? super A, ? extends B>> syncZip(AsyncFrame<A> asyncFrame, SyncFrame<B> syncFrame) {
            return f -> recurse(asyncSpine(asyncFrame, map(f, syncFrame)));
        }

        static <A, B> SyncFrame<A> syncFlatMap(Fn1<? super A, ? extends AsyncFrame<B>> fn, SyncFrame<B> syncFrame) {
            return a -> recurse(asyncSpine(fn.apply(a), syncFrame));
        }
    }

    static void main(String[] args) {
        int n = 20_000_000;
        ForkJoinPool ex = commonPool();
        AsyncFrame<Integer> deeplyLeftAssociated = times(
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
                ExecutionSpine application = asyncSpine(deeplyLeftAssociated, SyncFrame.syncFrame(x -> {
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
