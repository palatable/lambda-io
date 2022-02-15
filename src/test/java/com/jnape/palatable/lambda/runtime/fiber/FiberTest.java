package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn1;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.functions.Fn2.curried;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Constantly.constantly;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Eq.eq;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functions.builtin.fn4.IfThenElse.ifThenElse;
import static com.jnape.palatable.lambda.runtime.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.runtime.fiber.scheduler.testsupport.ExplodingScheduler.explodingScheduler;
import static com.jnape.palatable.lambda.runtime.fiber.scheduler.testsupport.SameThreadScheduler.sameThreadScheduler;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.Tags.STACK_SAFETY;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.CancelledResultMatcher.isCancelled;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FailedResultMatcher.failsWith;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FiberMatcher.whenExecuted;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.SuccessfulResultMatcher.succeeds;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.SuccessfulResultMatcher.succeedsWith;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.SuccessfulResultMatcher.successfully;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class FiberTest {

    @Nested
    public final class Failed {
        private static final IllegalStateException REASON = new IllegalStateException();

        @Test
        public void happyPath() {
            assertThat(Fiber.failed(REASON), whenExecuted(failsWith(REASON)));
        }

        @Test
        public void ignoresCancellation() {
            Canceller canceller = Canceller.root();
            canceller.cancel();
            assertThat(Fiber.failed(REASON), whenExecuted(canceller, failsWith(REASON)));
        }

        @Test
        public void ignoresScheduler() {
            assertThat(Fiber.failed(REASON), whenExecuted(explodingScheduler(), failsWith(REASON)));
        }
    }

    @Nested
    public final class Cancelled {

        @Test
        public void happyPath() {
            assertThat(Fiber.cancelled(), whenExecuted(isCancelled()));
        }

        @Test
        public void singleton() {
            assertSame(Fiber.cancelled(), Fiber.cancelled());
        }

        @Test
        public void ignoresScheduler() {
            assertThat(Fiber.cancelled(), whenExecuted(explodingScheduler(), isCancelled()));
        }
    }

    @Nested
    public final class Successful {

        @Test
        public void happyPath() {
            assertThat(Fiber.successful(1), whenExecuted(succeedsWith(1)));
        }

        @Test
        public void unitConvenience() {
            assertThat(Fiber.successful(), whenExecuted(succeedsWith(UNIT)));
        }

        @Test
        public void unitSingleton() {
            assertSame(Fiber.successful(), Fiber.successful());
        }

        @Test
        public void singleton() {
            assertSame(Fiber.cancelled(), Fiber.cancelled());
        }

        @Test
        public void ignoresScheduler() {
            assertThat(Fiber.cancelled(), whenExecuted(explodingScheduler(), isCancelled()));
        }
    }

    @Test
    public void park() {
        long start = System.currentTimeMillis();
        assertThat(Fiber.park(Duration.ofMillis(100)).bind(__ -> Fiber.successful(System.currentTimeMillis() - start)),
                   whenExecuted(successfully(greaterThan(100L))));
    }

    @Test
    public void never() {
        assertSame(Fiber.never(), Fiber.never());
        try {
            new CompletableFuture<>() {{
                Fiber.never().execute(sameThreadScheduler(), Canceller.root(), this::complete);
            }}.get(50, MILLISECONDS);
            fail("Expected Fiber.never() to timeout, but it terminated.");
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            assertInstanceOf(TimeoutException.class, e);
        }
    }

    @Test
    public void withCancellation() {
        assertThat(Fiber.withCancellation(Fiber.successful()), whenExecuted(succeeds()));

        Canceller canceller = Canceller.root();
        canceller.cancel();
        assertThat(Fiber.withCancellation(fiber(() -> fail("Expected fiber not to have been executed, but it was."))),
                   whenExecuted(canceller, isCancelled()));
    }

    @Test
    public void bind() {
        Throwable                    reason1            = new IllegalStateException();
        Throwable                    reason2            = new IllegalArgumentException();
        Fn1<Integer, Fiber<Integer>> successfully       = x -> Fiber.successful(x + 1);
        Fn1<Integer, Fiber<Integer>> failingWithReason2 = constantly(Fiber.failed(reason2));
        Fn1<Integer, Fiber<Integer>> cancelling         = constantly(Fiber.cancelled());

        Fiber<Integer> successful = Fiber.successful(1);
        assertThat(successful.bind(successfully), whenExecuted(succeedsWith(2)));
        assertThat(successful.bind(failingWithReason2), whenExecuted(failsWith(reason2)));
        assertThat(successful.bind(cancelling), whenExecuted(isCancelled()));

        Fiber<Integer> failed = Fiber.failed(reason1);
        assertThat(failed.bind(successfully), whenExecuted(failsWith(reason1)));
        assertThat(failed.bind(failingWithReason2), whenExecuted(failsWith(reason1)));
        assertThat(failed.bind(cancelling), whenExecuted(failsWith(reason1)));

        Fiber<Integer> cancelled = Fiber.cancelled();
        assertThat(cancelled.bind(successfully), whenExecuted(isCancelled()));
        assertThat(cancelled.bind(failingWithReason2), whenExecuted(isCancelled()));
        assertThat(cancelled.bind(cancelling), whenExecuted(isCancelled()));
    }

    @Nested
    @Tag(STACK_SAFETY)
    public final class BindStackSafety {

        @Test
        public void leftAssociated() {
            assertThat(times(100_000, fib -> fib.bind(constantly(Fiber.successful())), Fiber.successful()),
                       whenExecuted(succeeds()));
        }

        @Test
        public void rightAssociated() {
            assertThat(Fn1.<Integer, Fiber<Unit>>withSelf(curried(f -> ifThenElse(
                                       eq(0),
                                       constantly(Fiber.successful()),
                                       x -> Fiber.successful().bind(f.thunk(x - 1)))))
                               .apply(100_000),
                       whenExecuted(succeeds()));
        }
    }

}