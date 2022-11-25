package com.jnape.palatable.lambda.runtime.fiber;

import org.junit.jupiter.api.Test;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.runtime.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FiberMatcher.whenExecuted;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FiberTest {

    @Test
    public void succeeded() {
        assertThat(Fiber.succeeded(1), whenExecuted(equalTo(success(1))));
        assertThat(Fiber.succeeded(), allOf(whenExecuted(equalTo(success(UNIT))),
                                            sameInstance(Fiber.succeeded())));
    }

    @Test
    public void failed() {
        Throwable cause = new RuntimeException();
        assertThat(Fiber.failed(cause), whenExecuted(equalTo(failure(cause))));
    }

    @Test
    public void cancelled() {
        assertThat(Fiber.cancelled(), allOf(whenExecuted(equalTo(cancellation())),
                                            sameInstance(Fiber.cancelled())));
    }
}