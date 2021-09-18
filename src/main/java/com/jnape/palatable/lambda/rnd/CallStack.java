package com.jnape.palatable.lambda.rnd;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.recursion.RecursiveResult;

import static com.jnape.palatable.lambda.functions.recursion.Trampoline.trampoline;

public interface CallStack {

    <R> R evaluate(Interpreter<R> interpreter);

    default Unit collapse() {
        return trampoline(
                es -> es.evaluate(
                        new Interpreter<RecursiveResult<CallStack, Unit>>() {
                            @Override
                            public <Dependency> RecursiveResult<CallStack, Unit> apply(
                                    StackFrame<Dependency> stackFrame, Dependency dependency) {
                                return stackFrame.pop(dependency);
                            }
                        }
                ),
                this);
    }

    interface Interpreter<R> {
        <Context> R apply(StackFrame<Context> stackFrame, Context context);
    }

    static <Context> CallStack executionSpine(StackFrame<Context> stackFrame, Context context) {
        return new CallStack() {
            @Override
            public <R> R evaluate(Interpreter<R> interpreter) {
                return interpreter.apply(stackFrame, context);
            }
        };
    }
}
