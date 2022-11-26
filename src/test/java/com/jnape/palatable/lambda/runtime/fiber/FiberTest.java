package com.jnape.palatable.lambda.runtime.fiber;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jnape.palatable.lambda.internal.Runtime.throwChecked;
import static com.jnape.palatable.lambda.runtime.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.runtime.fiber.Result.failure;
import static com.jnape.palatable.lambda.runtime.fiber.Result.success;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FiberResultMatcher.yieldsPureResult;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FiberResultMatcher.yieldsResult;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FiberTimeoutMatcher.timesOutAfter;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class FiberTest {

    @Test
    public void result() {
        Result<Integer> result = success(1);
        assertThat(Fiber.result(result), yieldsPureResult(result));
    }

    @Test
    public void succeeded() {
        assertThat(Fiber.succeeded(1), yieldsPureResult(success(1)));
        assertThat(Fiber.succeeded(), allOf(yieldsPureResult(sameInstance(Result.success())),
                                            sameInstance(Fiber.succeeded())));
    }

    @Test
    public void failed() {
        Throwable cause = new RuntimeException();
        assertThat(Fiber.failed(cause), yieldsPureResult(failure(cause)));
    }

    @Test
    public void cancelled() {
        assertThat(Fiber.cancelled(), allOf(yieldsPureResult(sameInstance(Result.cancellation())),
                                            sameInstance(Fiber.cancelled())));
    }

    @Test
    public void never() {
        assertThat(Fiber.never(), allOf(timesOutAfter(Duration.ofMillis(50)),
                                        sameInstance(Fiber.never())));
    }

    @Test
    public void fiberExecutesFunctionOnScheduler() {
        List<String> interactions = new ArrayList<>();

        assertThat(fiber(() -> {
            interactions.add("running");
            return interactions;
        }), yieldsResult(runnable -> {
            interactions.add("scheduled");
            runnable.run();
        }, equalTo(success(List.of("scheduled", "running")))));
    }

    @Test
    public void fiberCatchesThrowableFromFunction() {
        Throwable throwable = new Exception("blew up");
        assertThat(fiber(() -> {
            throw throwable;
        }), yieldsResult(equalTo(failure(throwable))));
    }

    @Test
    public void fiberDoesNotCatchThrowableFromCallback() {
        Throwable     throwable         = new Exception("blew up");
        AtomicInteger invocationCounter = new AtomicInteger(0);
        try {
            fiber(() -> 1).execute(Runnable::run, res -> {
                invocationCounter.incrementAndGet();
                throw throwChecked(throwable);
            });
            fail("Expected callback to throw, but didn't.");
        } catch (Throwable thrown) {
            assertSame(throwable, thrown, "Expected to throw throwable, but threw <" + thrown + ">");
            int invocations = invocationCounter.get();
            assertEquals(1, invocations, "Expected exactly one invocation of callback, but there were <"
                    + invocations + ">");
        }
    }
}