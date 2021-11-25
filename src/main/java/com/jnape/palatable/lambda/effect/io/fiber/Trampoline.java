package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.Executor;

public final class Trampoline {

    private final Executor executor;

    private Trampoline(Executor executor) {
        this.executor = executor;
    }


}
