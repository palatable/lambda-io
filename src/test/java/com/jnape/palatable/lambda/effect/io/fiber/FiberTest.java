package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher.FiberResultMatcher;
import com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher.FiberTimeoutMatcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.internal.Runtime.throwChecked;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.succeeded;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher.FiberResultMatcher.yieldsPureResult;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher.FiberResultMatcher.yieldsResult;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher.FiberTimeoutMatcher.timesOutAfter;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class FiberTest {

    @Nested
    public class Value {

        @Test
        public void resultIsPure() {
            Result<Integer> result = Result.success(1);
            assertThat(Fiber.result(result), FiberResultMatcher.yieldsPureResult(result));
        }

        @Test
        public void succeededConvenienceMethods() {
            assertThat(Fiber.succeeded(1), FiberResultMatcher.yieldsPureResult(Result.success(1)));
            assertThat(Fiber.succeeded(), Matchers.allOf(FiberResultMatcher.yieldsPureResult(sameInstance(Result.success())),
                                                         sameInstance(Fiber.succeeded())));
        }

        @Test
        public void failedConvenientMethod() {
            Throwable cause = new RuntimeException();
            assertThat(Fiber.failed(cause), FiberResultMatcher.yieldsPureResult(Result.failure(cause)));
        }

        @Test
        public void cancelledConvenienceMethod() {
            assertThat(Fiber.cancelled(), Matchers.allOf(FiberResultMatcher.yieldsPureResult(sameInstance(Result.cancellation())),
                                                         sameInstance(Fiber.cancelled())));
        }
    }

    @Nested
    public class Suspension {

        @Test
        public void runnableConvenienceMethod() {
            AtomicBoolean ref = new AtomicBoolean();
            assertThat(Fiber.fiber(() -> ref.set(true)), FiberResultMatcher.yieldsResult(equalTo(Result.success())));
            assertTrue(ref.get());
        }

        @Test
        public void executesFunctionOnScheduler() {
            List<String> interactions = new ArrayList<>();

            assertThat(Fiber.fiber(() -> {
                interactions.add("running");
                return interactions;
            }), FiberResultMatcher.yieldsResult(runnable -> {
                interactions.add("scheduled");
                runnable.run();
            }, Canceller.canceller(), equalTo(Result.success(List.of("scheduled", "running")))));
        }

        @Test
        public void catchesThrowableFromFunction() {
            RuntimeException throwable = new IllegalStateException("blew up");
            assertThat(Fiber.fiber(() -> {
                throw throwable;
            }), FiberResultMatcher.yieldsResult(equalTo(Result.failure(throwable))));
        }

        @Test
        public void doesNotCatchThrowableFromCallback() {
            Throwable     throwable         = new Exception("blew up");
            AtomicInteger invocationCounter = new AtomicInteger(0);
            try {
                Fiber.fiber(() -> 1).execute(Runnable::run, Canceller.canceller(), res -> {
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
            Canceller canceller = Canceller.canceller();
            canceller.cancel();
            AtomicBoolean invoked = new AtomicBoolean(false);
            assertThat(Fiber.fiber(() -> {
                invoked.set(true);
                return 1;
            }), FiberResultMatcher.yieldsResult(canceller, equalTo(Result.cancellation())));
            assertFalse(invoked.get());
        }
    }

    @Nested
    public class Bind {

        @Test
        public void cancellationPreemptsBind() {
            assertThat(Fiber.fiber(() -> 1).bind(x -> Fiber.fiber(() -> x + 1)),
                       FiberResultMatcher.yieldsResult(equalTo(Result.success(2))));
        }

        @Nested
        public class StackSafety {

            @Test
            public void leftBind() {
                int n = 50_000;
                assertThat(times(n, f -> f.bind(x -> Fiber.fiber(() -> x + 1)), Fiber.succeeded(0)),
                           FiberResultMatcher.yieldsResult(equalTo(Result.success(n))));
            }
        }
    }

    @Nested
    public class Race {

        @Test
        public void cancellationPreemptsRace() {
            Canceller canceller = Canceller.canceller();
            canceller.cancel();
            assertThat(Fiber.race(Fiber.succeeded(1), Fiber.succeeded(2)),
                       FiberResultMatcher.yieldsPureResult(equalTo(Result.cancellation())));
        }

        @Test
        public void firstFinisherWins() {
            assertThat(Fiber.race(Fiber.succeeded(1), Fiber.never()),
                       FiberResultMatcher.yieldsResult(equalTo(Result.success(1))));
            assertThat(Fiber.race(Fiber.never(), Fiber.succeeded(2)),
                       FiberResultMatcher.yieldsResult(equalTo(Result.success(2))));
        }

        @Test
        public void losingFiberIsCancelled() {
            AtomicBoolean loserExecuted = new AtomicBoolean(false);
            assertThat(Fiber.race(Fiber.succeeded(), Fiber.fiber(() -> loserExecuted.set(true))),
                       FiberResultMatcher.yieldsResult(equalTo(Result.success())));
            assertFalse(loserExecuted.get());
        }

        @Test
        public void cancellationAfterStartStillCancels() {
            Canceller canceller = Canceller.canceller();

            AtomicInteger  invocations = new AtomicInteger(0);
            Fiber<Integer> fiber       = Fiber.fiber(invocations::incrementAndGet);
            assertThat(Fiber.race(fiber, fiber),
                       FiberResultMatcher.yieldsResult(runnable -> {
                           canceller.cancel();
                           runnable.run();
                       }, canceller, equalTo(Result.cancellation())));
        }
    }

    @Nested
    public class Never {

        @Test
        public void neverInvokesCallback() {
            assertThat(Fiber.never(), Matchers.allOf(FiberTimeoutMatcher.timesOutAfter(Duration.ofMillis(50)),
                                                     sameInstance(Fiber.never())));
        }

        @Test
        public void neverCannotBeCancelled() {
            Canceller canceller = Canceller.canceller();
            canceller.cancel();
            assertThat(Fiber.never(), FiberTimeoutMatcher.timesOutAfter(canceller, Duration.ofMillis(50)));
        }
    }
}