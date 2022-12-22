package com.jnape.palatable.lambda.effect.io.fiber;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jnape.palatable.lambda.effect.io.fiber.Canceller.canceller;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.cancelled;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.failed;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.never;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.race;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.result;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.succeeded;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static com.jnape.palatable.lambda.effect.io.fiber.Trampoline.trampoline;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher.FiberResultMatcher.yieldsResult;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher.FiberTimeoutMatcher.timesOutAfter;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.internal.Runtime.throwChecked;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TrampolineTest {

    @Nested
    public class Value {

        @Test
        public void happyPath() {
            Result<Integer> result = success(1);
            assertThat(result(result), yieldsResult(result));
        }

        @Test
        public void succeededConvenienceMethods() {
            assertThat(succeeded(1), yieldsResult(success(1)));
            assertThat(succeeded(), allOf(yieldsResult(sameInstance(success())),
                                          sameInstance(succeeded())));
        }

        @Test
        public void failedConvenienceMethod() {
            RuntimeException cause = new RuntimeException("kaboom");
            assertThat(failed(cause), yieldsResult(failure(cause)));
        }

        @Test
        public void cancelledConvenienceMethod() {
            assertThat(cancelled(), allOf(yieldsResult(sameInstance(cancellation())),
                                          sameInstance(cancelled())));
        }
    }

    @Nested
    public class Suspension {

        @Test
        public void runnableConvenienceMethod() {
            new AtomicBoolean() {{
                assertFalse(get());
                assertThat(fiber(() -> set(true)), yieldsResult(equalTo(success())));
                assertTrue(get());
            }};
        }

        @Test
        public void executesFunctionOnScheduler() {
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
                trampoline(Runnable::run).unsafeRunAsync(fiber(() -> 1), res -> {
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
            }), yieldsResult(canceller, equalTo(cancellation())));
            assertFalse(invoked.get());
        }
    }

    @Nested
    //todo: failure and cancellation cases
    public class Bind {

        @Test
        //todo: huh?
        public void cancellationPreemptsBind() {
            assertThat(fiber(() -> 1).bind(x -> fiber(() -> x + 1)),
                       yieldsResult(equalTo(success(2))));
        }

        @Nested
        @Tag("safety:stack")
        public class StackSafety {

            @Test
            public void leftBind() {
                int n = 50_000;
                assertThat(times(n, f -> f.bind(x -> fiber(() -> x + 1)), succeeded(0)),
                           yieldsResult(equalTo(success(n))));
            }

            //todo: right bind, mixed bind
        }
    }

    @Nested
    public class Race {

        @Test
        public void cancellationPreemptsRace() {
            Canceller canceller = canceller();
            canceller.cancel();
            assertThat(race(succeeded(1), succeeded(2)),
                       yieldsResult(canceller, equalTo(cancellation())));
        }

        @Test
        public void firstFinisherWins() {
            assertThat(race(succeeded(1), never()),
                       yieldsResult(equalTo(success(1))));
            assertThat(race(never(), succeeded(2)),
                       yieldsResult(equalTo(success(2))));
        }

        @Test
        public void losingFiberIsCancelled() {
            AtomicBoolean loserExecuted = new AtomicBoolean(false);
            assertThat(race(succeeded(), fiber(() -> loserExecuted.set(true))),
                       yieldsResult(equalTo(success())));
            assertFalse(loserExecuted.get());
        }

        @Test
        public void cancellationAfterStartStillCancels() {
            Canceller canceller = canceller();

            AtomicInteger  invocations = new AtomicInteger(0);
            Fiber<Integer> fiber       = fiber(invocations::incrementAndGet);
            assertThat(race(fiber, fiber),
                       yieldsResult(runnable -> {
                           canceller.cancel();
                           runnable.run();
                       }, canceller, equalTo(cancellation())));
        }

        @Test
        @Disabled("these are blocking the runtime! revisit with with delayed scheduling")
        //todo: these are blocking the runtime! revisit with with delayed scheduling
        public void sanityCheck() {
            assertThat(race(fiber(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                return 1;
            }), fiber(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                return 2;
            })), yieldsResult(Executors.newFixedThreadPool(2)::execute, equalTo(success(2))));
        }
    }

    @Nested
    public class Never {

        @Test
        public void singleton() {
            assertThat(never(), sameInstance(never()));
        }

        @Test
        public void neverInvokesCallback() {
            assertThat(never(), timesOutAfter(Duration.ofMillis(50)));
        }

        @Test
        public void neverCanBeCancelled() {
            Canceller canceller = canceller();
            canceller.cancel();
            assertThat(never(), yieldsResult(canceller, equalTo(cancellation())));
        }
    }

}