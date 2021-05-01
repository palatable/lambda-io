package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;

public interface ExecutionSpine {

    <R> R evaluate(SyncPhi<R> syncPhi, AsyncPhi<R> asyncPhi);

    default Unit collapse() {
        return trampoline(
                es -> es.evaluate(
                        new SyncPhi<RecursiveResult<ExecutionSpine, Unit>>() {
                            @Override
                            public <A> RecursiveResult<ExecutionSpine, Unit> apply(ExecutionFrame<A> syncFrame, A a) {
                                return syncFrame.pop(a);
                            }
                        },
                        new AsyncPhi<RecursiveResult<ExecutionSpine, Unit>>() {
                            @Override
                            public <A> RecursiveResult<ExecutionSpine, Unit> apply(
                                    ExecutionFrame<ExecutionFrame<A>> asyncFrame,
                                    ExecutionFrame<A> syncFrame) {
                                return asyncFrame.pop(syncFrame);
                            }
                        }),
                this);
    }

    interface AsyncPhi<R> {
        <A> R apply(ExecutionFrame<ExecutionFrame<A>> asyncFrame, ExecutionFrame<A> syncFrame);
    }

    interface SyncPhi<R> {
        <A> R apply(ExecutionFrame<A> syncFrame, A a);
    }

    static <A> ExecutionSpine asyncSpine(ExecutionFrame<ExecutionFrame<A>> asyncFrame, ExecutionFrame<A> syncFrame) {
        return new ExecutionSpine() {
            @Override
            public <R> R evaluate(SyncPhi<R> syncPhi, AsyncPhi<R> asyncPhi) {
                return asyncPhi.apply(asyncFrame, syncFrame);
            }
        };
    }

    static <A> ExecutionSpine syncSpine(ExecutionFrame<A> syncFrame, A a) {
        return new ExecutionSpine() {
            @Override
            public <R> R evaluate(SyncPhi<R> syncPhi, AsyncPhi<R> asyncPhi) {
                return syncPhi.apply(syncFrame, a);
            }
        };
    }
}
