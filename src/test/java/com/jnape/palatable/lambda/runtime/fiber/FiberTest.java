package com.jnape.palatable.lambda.runtime.fiber;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.runtime.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FiberResultMatcher.yieldsPureResult;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FiberTimeoutMatcher.timesOutAfter;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FiberTest {

    @Test
    public void result() {
        Result<Integer> result = success(1);
        assertThat(Fiber.result(result), yieldsPureResult(result));
    }

    @Test
    public void succeeded() {
        assertThat(Fiber.succeeded(1), yieldsPureResult(success(1)));
        assertThat(Fiber.succeeded(), allOf(yieldsPureResult(success(UNIT)),
                                            sameInstance(Fiber.succeeded())));
    }

    @Test
    public void failed() {
        Throwable cause = new RuntimeException();
        assertThat(Fiber.failed(cause), yieldsPureResult(failure(cause)));
    }

    @Test
    public void cancelled() {
        assertThat(Fiber.cancelled(), allOf(yieldsPureResult(cancellation()),
                                            sameInstance(Fiber.cancelled())));
    }

    @Test
    public void never() {
        assertThat(Fiber.never(), timesOutAfter(Duration.ofMillis(50)));
    }
}