package com.jnape.palatable.lambda.effect.io.fiber.testsupport;

import com.jnape.palatable.lambda.effect.io.fiber.Fiber;

import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;

public final class Never {

    public static <A> Fiber<A> never() {
        return fiber(k -> {});
    }
}
