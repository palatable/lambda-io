package com.jnape.palatable.lambda.rnd;

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
import static com.jnape.palatable.lambda.rnd.CallStack.executionSpine;
import static java.util.concurrent.ForkJoinPool.commonPool;

public interface StackFrame<Context> {
    RecursiveResult<CallStack, Unit> pop(Context context);

    static <A> StackFrame<StackFrame<A>> async(Fn1<Fn1<? super A, ? extends Unit>, Unit> k) {
        return syncFrame -> {
            // todo: disaster
            AtomicBoolean                inBand = new AtomicBoolean(true);
            CompletableFuture<CallStack> staged = new CompletableFuture<>();
            k.apply(a -> {
                if (inBand.get()) {
                    staged.complete(executionSpine(syncFrame, a));
                    return UNIT;
                }
                staged.complete(null);
                return executionSpine(syncFrame, a).collapse();
            });
            inBand.set(false);

            return maybe(staged.join())
                    .match(fn0(() -> terminate(UNIT)),
                           RecursiveResult::recurse);
        };
    }

    static <A> StackFrame<StackFrame<A>> fork(Fn0<A> fn0, Executor executor) {
        return async(f -> {
            executor.execute(() -> f.apply(fn0.apply()));
            return UNIT;
        });
    }

    static <A> StackFrame<StackFrame<A>> value(A a) {
        return syncFrame -> syncFrame.pop(a);
    }

    static <A> StackFrame<A> callback(Fn1<? super A, ? extends Unit> k) {
        return a -> terminate(k.apply(a));
    }

    static <Z, A> StackFrame<StackFrame<A>> map(Fn1<? super Z, ? extends A> fn,
                                                StackFrame<StackFrame<Z>> asyncFrame) {
        return syncFrame -> recurse(executionSpine(asyncFrame, contraMap(fn, syncFrame)));
    }

    static <A, B> StackFrame<A> contraMap(Fn1<? super A, ? extends B> fn, StackFrame<B> syncFrame) {
        return a -> recurse(executionSpine(syncFrame, fn.apply(a)));
    }

    static <Z, A> StackFrame<StackFrame<A>> zip(
            StackFrame<StackFrame<Fn1<? super Z, ? extends A>>> asyncFrameF,
            StackFrame<StackFrame<Z>> asyncFrame) {
        return syncFrame -> recurse(executionSpine(asyncFrameF, link(asyncFrame, syncFrame)));
    }

    static <Z, A> StackFrame<StackFrame<A>> flatMap(StackFrame<StackFrame<Z>> asyncFrame,
                                                    Fn1<? super Z, ? extends StackFrame<StackFrame<A>>> fn) {
        return syncFrame -> recurse(executionSpine(asyncFrame, flatMap(fn, syncFrame)));
    }

    static <A, B> StackFrame<Fn1<? super A, ? extends B>> link(StackFrame<StackFrame<A>> asyncFrame,
                                                               StackFrame<B> syncFrame) {
        return f -> recurse(executionSpine(asyncFrame, contraMap(f, syncFrame)));
    }

    static <A, B> StackFrame<A> flatMap(Fn1<? super A, ? extends StackFrame<B>> fn, B b) {
        return a -> recurse(executionSpine(fn.apply(a), b));
    }

    static void main(String[] args) {
        int          n  = 20_000_000;
        ForkJoinPool ex = commonPool();
        StackFrame<StackFrame<Integer>> deeplyLeftAssociated = times(
                n,
                f -> map(x -> x + 1, f),
                async(k -> {
                    ex.execute(() -> {
                        System.out.println("Running...");
                        k.apply(42);
                    });
                    return UNIT;
                }));

        int i = 0;
        while (i++ < 10) {
            new CompletableFuture<Integer>() {{
                CallStack application = executionSpine(deeplyLeftAssociated, callback(x -> {
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
