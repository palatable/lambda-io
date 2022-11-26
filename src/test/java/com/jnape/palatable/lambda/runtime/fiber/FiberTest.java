package com.jnape.palatable.lambda.runtime.fiber;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jnape.palatable.lambda.internal.Runtime.throwChecked;
import static com.jnape.palatable.lambda.runtime.fiber.Canceller.canceller;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class FiberTest {

    @Nested
    public class Value {

        @Test
        public void resultIsPure() {
            Result<Integer> result = success(1);
            assertThat(Fiber.result(result), yieldsPureResult(result));
        }

        @Test
        public void succeededConvenienceMethods() {
            assertThat(Fiber.succeeded(1), yieldsPureResult(success(1)));
            assertThat(Fiber.succeeded(), allOf(yieldsPureResult(sameInstance(Result.success())),
                                                sameInstance(Fiber.succeeded())));
        }

        @Test
        public void failedConvenientMethod() {
            Throwable cause = new RuntimeException();
            assertThat(Fiber.failed(cause), yieldsPureResult(failure(cause)));
        }

        @Test
        public void cancelledConvenienceMethod() {
            assertThat(Fiber.cancelled(), allOf(yieldsPureResult(sameInstance(Result.cancellation())),
                                                sameInstance(Fiber.cancelled())));
        }
    }

    @Nested
    public class Suspension {

        @Test
        public void executesFunctionOnScheduler() {
            List<String> interactions = new ArrayList<>();

            assertThat(fiber(() -> {
                interactions.add("running");
                return interactions;
            }), yieldsResult(runnable -> {
                interactions.add("scheduled");
                runnable.run();
            }, canceller(), equalTo(success(List.of("scheduled", "running")))));
        }

        @Test
        public void catchesThrowableFromFunction() {
            RuntimeException throwable = new IllegalStateException("blew up");
            assertThat(fiber(() -> {
                throw throwable;
            }), yieldsResult(equalTo(failure(throwable))));
        }

        @Test
        public void doesNotCatchThrowableFromCallback() {
            Throwable     throwable         = new Exception("blew up");
            AtomicInteger invocationCounter = new AtomicInteger(0);
            try {
                fiber(() -> 1).execute(Runnable::run, canceller(), res -> {
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

        @Test
        public void fiberCancellationPreemptsScheduling() {
            Canceller canceller = canceller();
            canceller.cancel();
            AtomicBoolean invoked = new AtomicBoolean(false);
            assertThat(fiber(() -> {
                invoked.set(true);
                return 1;
            }), yieldsResult(canceller, equalTo(Result.cancellation())));
            assertFalse(invoked.get());
        }

        @Test
        public void fiberCancellationPreemptsExecutionAfterScheduling() {
            Canceller canceller = canceller();

            AtomicBoolean invoked = new AtomicBoolean(false);
            assertThat(fiber(() -> {
                invoked.set(true);
                return 1;
            }), yieldsResult(r -> {
                canceller.cancel();
                r.run();
            }, canceller, equalTo(Result.cancellation())));
            assertFalse(invoked.get());
        }
    }

    @Nested
    public class Never {

        @Test
        public void neverInvokesCallback() {
            assertThat(Fiber.never(), allOf(timesOutAfter(Duration.ofMillis(50)),
                                            sameInstance(Fiber.never())));
        }

        @Test
        public void neverCannotBeCancelled() {
            Canceller canceller = canceller();
            canceller.cancel();
            assertThat(Fiber.never(), timesOutAfter(canceller, Duration.ofMillis(50)));
        }
    }
}