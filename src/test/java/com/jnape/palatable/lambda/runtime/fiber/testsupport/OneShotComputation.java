package com.jnape.palatable.lambda.runtime.fiber.testsupport;

import com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FiberMatcher;

import java.util.function.Supplier;

public final class OneShotComputation<A> {

    private final    Supplier<A> computation;
    private volatile A           result;
    private volatile boolean     computed;

    private OneShotComputation(Supplier<A> computation) {
        this.computation = computation;
        result           = null;
        computed         = false;
    }

    public A getOrCompute() {
        if (!computed) {
            synchronized (this) {
                if (!computed) {
                    result   = computation.get();
                    computed = true;
                }
            }
        }
        return result;
    }

    public static <A> OneShotComputation<A> oneShotComputation(Supplier<A> computation) {
        return new OneShotComputation<>(computation);
    }
}
