package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jnape.palatable.lambda.effect.io.fiber.Canceller.canceller;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.cancelled;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.delay;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.failed;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.forever;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.never;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.parallel;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.pin;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.race;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.result;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.succeeded;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static com.jnape.palatable.lambda.effect.io.fiber.Trampoline.trampoline;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher.FiberResultMatcher.yieldsResult;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler.DecoratingScheduler.before;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler.SameThread.sameThread;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.internal.Runtime.throwChecked;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Timeout(value = 100, unit = MILLISECONDS)
public class TrampolineTest {

    private static final RuntimeException CAUSE = new RuntimeException("blew up");

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
            assertThat(failed(CAUSE), yieldsResult(failure(CAUSE)));
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
            }), yieldsResult(before(sameThread(), __ -> interactions.add("scheduled")),
                             equalTo(success(List.of("scheduled", "running")))));
        }

        @Test
        public void catchesThrowableFromFunction() {
            assertThat(fiber(() -> {
                throw CAUSE;
            }), yieldsResult(equalTo(failure(CAUSE))));
        }

        @Test
        //todo: this isn't a reliable way to demonstrate the point...
        public void doesNotCatchThrowableFromCallback() {
            AtomicInteger invocationCounter = new AtomicInteger(0);
            try {
                trampoline(sameThread(), sameThread()).unsafeRunAsync(fiber(() -> 1), res -> {
                    invocationCounter.incrementAndGet();
                    throw throwChecked(CAUSE);
                });
                fail("Expected callback to throw, but didn't.");
            } catch (Throwable thrown) {
                assertSame(CAUSE, thrown, "Expected to throw throwable, but threw <" + thrown + ">");
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
    public class Bind {

        @Test
        public void happyPath() {
            assertThat(fiber(() -> 1).bind(x -> fiber(() -> x + 1)),
                       yieldsResult(equalTo(success(2))));
        }

        @Test
        public void failurePreemptsSubsequentBinds() {
            AtomicBoolean downstreamCalled = new AtomicBoolean(false);
            assertThat(fiber(() -> {throw CAUSE;}).bind(x -> fiber(() -> downstreamCalled.set(true))),
                       yieldsResult(failure(CAUSE)));
            assertFalse(downstreamCalled.get());
        }

        @Test
        public void cancellationPreemptsSubsequentBinds() {
            AtomicBoolean downstreamCalled = new AtomicBoolean(false);
            assertThat(cancelled().bind(x -> fiber(() -> downstreamCalled.set(true))),
                       yieldsResult(cancellation()));
            assertFalse(downstreamCalled.get());
        }

        @Nested
        public class StackSafety {

            private static final int STACK_EXPLODING_NUMBER = 50_000;

            @Test
            public void deeplyLeftAssociated() {
                assertThat(times(STACK_EXPLODING_NUMBER, f -> f.bind(x -> fiber(() -> x + 1)), succeeded(0)),
                           yieldsResult(equalTo(success(STACK_EXPLODING_NUMBER))));
            }

            @Test
            public void deeplyRightAssociated() {
                assertThat(Fn1.<Integer, Fiber<Integer>>withSelf(
                                           (f, i) -> i == STACK_EXPLODING_NUMBER
                                                     ? succeeded(0)
                                                     : succeeded(1)
                                                             .bind(x -> f.apply(i + 1)
                                                                     .bind(y -> succeeded(x + y))))
                                   .apply(0),
                           yieldsResult(equalTo(success(STACK_EXPLODING_NUMBER))));
            }
        }
    }

    @Nested
    public class Forever {

        @Test
        public void successRepeatsIndefinitely() {
            AtomicInteger counter = new AtomicInteger();
            assertThat(forever(fiber(counter::incrementAndGet).bind(x -> x == 3 ? cancelled() : succeeded(x))),
                       yieldsResult(cancellation()));
            assertEquals(3, counter.get());
        }

        @Test
        public void failureTerminatesImmediately() {
            AtomicInteger counter = new AtomicInteger();
            assertThat(forever(fiber(counter::incrementAndGet).bind(__ -> failed(CAUSE))),
                       yieldsResult(CAUSE));
            assertEquals(1, counter.get());
        }

        @Test
        public void cancellationTerminatesImmediately() {
            AtomicInteger counter = new AtomicInteger();
            assertThat(forever(fiber(counter::incrementAndGet).bind(__ -> cancelled())),
                       yieldsResult(cancellation()));
            assertEquals(1, counter.get());
        }
    }

    @Nested
    public class Race {

        @Test
        public void respectsCancellation() {
            Canceller canceller = canceller();
            canceller.cancel();
            assertThat(race(succeeded(1), succeeded(2)),
                       yieldsResult(canceller, equalTo(cancellation())));
        }

        @Test
        public void firstFinisherWins() {
            for (Result<Unit> result : Arrays.<Result<Unit>>asList(success(), failure(CAUSE), cancellation())) {
                assertThat(race(result(result), never()),
                           yieldsResult(equalTo(result)));
                assertThat(race(never(), result(result)),
                           yieldsResult(equalTo(result)));
            }
        }

        @Test
        public void losingFiberIsCancelled() {
            AtomicBoolean loserExecuted = new AtomicBoolean(false);
            assertThat(race(succeeded(), fiber(() -> loserExecuted.set(true))),
                       yieldsResult(equalTo(success())));
            assertFalse(loserExecuted.get());
        }

        @Test
        public void usesChildCanceller() {
            Canceller canceller = canceller();

            AtomicBoolean loserExecuted = new AtomicBoolean(false);
            assertThat(race(succeeded(), succeeded()).bind(Fiber::succeeded),
                       yieldsResult(canceller, equalTo(success())));
            assertFalse(loserExecuted.get());
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
            assertThrowsExactly(TimeoutException.class, () -> new CompletableFuture<>() {{
                trampoline(sameThread(), sameThread()).unsafeRunAsync(
                        never(),
                        this::complete
                );
            }}.get(50, MILLISECONDS));
        }

        @Test
        public void neverCanBeCancelled() {
            Canceller canceller = canceller();
            canceller.cancel();
            assertThat(never(), yieldsResult(canceller, equalTo(cancellation())));
        }
    }

    @Nested
    public class Delay {

        @Test
        //todo: "schedules on timer then resumes on scheduler" indicates names here are improvable;
        //      also, this test is just garbage, figure out a more robust way to demonstrate the assertion
        public void schedulesOnTimerThenResumesOnScheduler() {
            Fiber<Integer> delay      = delay(succeeded(1), ofNanos(1));
            List<String>   boundaries = new ArrayList<>();

            assertThat(delay, yieldsResult(
                    before(sameThread(), __ -> boundaries.add("scheduler")),
                    (runnable, d, tu) -> {
                        boundaries.add("timer: " + d + tu);
                        runnable.run();
                        return () -> {};
                    }, canceller(), equalTo(success(1))
            ));
            assertEquals(List.of("scheduler", "timer: 1NANOSECONDS", "scheduler"), boundaries);
        }

        @Test
        public void delayCallbackWiresIntoCancellation() {
            Canceller      canceller      = canceller();
            Fiber<Integer> delay          = delay(succeeded(1), ofNanos(1));
            AtomicBoolean  callbackCalled = new AtomicBoolean(false);
            assertThat(delay, yieldsResult(
                    (runnable, d, tu) -> {
                        runnable.run();
                        return () -> callbackCalled.set(true);
                    }, canceller, equalTo(success(1))
            ));
            assertFalse(callbackCalled.get());
            canceller.cancel();
            assertTrue(callbackCalled.get());
        }

        @Test
        public void automaticallyCancelsIfFailsRegisteringCancelCallback() {
            Canceller      canceller      = canceller();
            Fiber<Integer> delay          = delay(succeeded(1), ofNanos(1));
            AtomicBoolean  callbackCalled = new AtomicBoolean(false);
            assertThat(delay, yieldsResult(
                    (runnable, __, ___) -> {
                        canceller.cancel();
                        runnable.run();
                        return () -> callbackCalled.set(true);
                    }, canceller, equalTo(cancellation())
            ));
            assertTrue(callbackCalled.get());
        }
    }

    @Nested
    public class Pin {
        private List<String> interactions;

        private final Scheduler origin = before(sameThread(), __ -> interactions.add("on origin"));
        private final Scheduler target = before(sameThread(), __ -> interactions.add("on target"));

        @BeforeEach
        public void setUp() {
            interactions = new ArrayList<>();
        }

        @Test
        public void runsOnSeparateScheduler() {
            assertThat(pin(fiber((Runnable) () -> interactions.add("fiber")), target),
                       yieldsResult(origin, equalTo(success())));
            assertEquals(asList("on origin",
                                "on target",
                                "fiber",
                                "on origin"),
                         interactions);
        }

        @Test
        public void transfersBackToOriginalSchedulerAfterwards() {
            assertThat(fiber((Runnable) () -> interactions.add("before"))
                               .bind(__ -> pin(fiber((Runnable) () -> interactions.add("pinned")), target))
                               .bind(__ -> fiber((Runnable) () -> interactions.add("after"))),
                       yieldsResult(origin, equalTo(success())));
            assertEquals(asList("on origin",
                                "before",
                                "on target",
                                "pinned",
                                "on origin",
                                "after"),
                         interactions);
        }
    }

    @Nested
    public class Parallel {

        @Nested
        public class Success {

            @Test
            public void runsEachFiberOnScheduler() {
                List<String> interactions = new ArrayList<>();
                assertThat(parallel(fiber(() -> {
                                        interactions.add("1");
                                        return 1;
                                    }),
                                    fiber(() -> {
                                        interactions.add("2");
                                        return 2;
                                    }),
                                    fiber(() -> {
                                        interactions.add("3");
                                        return 3;
                                    })),
                           yieldsResult(before(sameThread(), __ -> interactions.add("schedule")),
                                        equalTo(success(asList(1, 2, 3)))));
                assertEquals(asList("schedule", "schedule", "schedule", "schedule", "1", "2", "3"), interactions);
            }

            @Test
            public void assemblesResultsInOrder() {
                assertThat(parallel(delay(succeeded(1), ofNanos(3)),
                                    delay(succeeded(2), ofNanos(2)),
                                    delay(succeeded(3), ofNanos(1))),
                           yieldsResult(success(asList(1, 2, 3))));
            }

            @Test
            public void parentCancellationBeforeRunPreemptsRun() {
                Canceller canceller = canceller();
                canceller.cancel();
                List<String> interactions = new ArrayList<>();
                assertThat(parallel(fiber(() -> {
                                        interactions.add("1");
                                        return 1;
                                    }),
                                    fiber(() -> {
                                        interactions.add("2");
                                        return 2;
                                    }),
                                    fiber(() -> {
                                        interactions.add("3");
                                        return 3;
                                    })),
                           yieldsResult(canceller, equalTo(cancellation())));
                assertEquals(emptyList(), interactions);
            }

            @Test
            public void parentCancellationAfterAllFibersAreScheduledStillCancels() {
                Canceller canceller = canceller();
                assertThat(parallel(succeeded(1), succeeded(2), fiber(() -> {
                               canceller.cancel();
                               return 3;
                           })),
                           yieldsResult(canceller, equalTo(cancellation())));
            }
        }

        @Nested
        public class Cancellation {

            @Test
            public void clobbersResult() {
                assertThat(parallel(succeeded(1), cancelled(), succeeded(3)),
                           yieldsResult(cancellation()));
            }

            @Test
            public void cancelsOtherFibers() {
                List<String> interactions = new ArrayList<>();
                assertThat(parallel(fiber(() -> interactions.add("a")), cancelled(), fiber(() -> interactions.add("c"))),
                           yieldsResult(cancellation()));
                assertEquals(singletonList("a"), interactions);
            }

            @Test
            public void cancellationBeforeFailureFavorsCancellation() {
                assertThat(parallel(succeeded(1), cancelled(), failed(CAUSE)),
                           yieldsResult(cancellation()));
            }
        }

        @Nested
        public class Failure {

            @Test
            public void clobbersResult() {
                assertThat(parallel(succeeded(1), failed(CAUSE), succeeded(3)),
                           yieldsResult(failure(CAUSE)));
            }

            @Test
            public void cancelsOtherFibers() {
                List<String> interactions = new ArrayList<>();
                assertThat(parallel(fiber(() -> interactions.add("a")), failed(CAUSE), fiber(() -> interactions.add("c"))),
                           yieldsResult(failure(CAUSE)));
                assertEquals(singletonList("a"), interactions);
            }

            @Test
            public void failureBeforeCancellationFavorsFailure() {
                assertThat(parallel(succeeded(1), failed(CAUSE), cancelled()),
                           yieldsResult(failure(CAUSE)));
            }
        }
    }
}