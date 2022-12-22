package com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.effect.io.fiber.Canceller;
import com.jnape.palatable.lambda.effect.io.fiber.Fiber;
import com.jnape.palatable.lambda.effect.io.fiber.Result;
import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import com.jnape.palatable.lambda.internal.Runtime;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static com.jnape.palatable.lambda.effect.io.fiber.Canceller.canceller;
import static com.jnape.palatable.lambda.effect.io.fiber.Trampoline.trampoline;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class FiberTimeoutMatcher<A> extends TypeSafeMatcher<Fiber<? extends A>> {

    private final Scheduler scheduler;
    private final Canceller canceller;
    private final Duration  timeout;

    private CompletableFuture<Result<? extends A>> result;

    private FiberTimeoutMatcher(Scheduler scheduler, Canceller canceller, Duration timeout) {
        this.scheduler = scheduler;
        this.canceller = canceller;
        this.timeout   = timeout;
    }

    @Override
    protected synchronized boolean matchesSafely(Fiber<? extends A> fiber) {
        if (result == null)
            result = new CompletableFuture<>() {{
                trampoline(() -> canceller, scheduler).unsafeRunAsync(fiber, this::complete);
            }};

        try {
            result.get(timeout.toMillis(), MILLISECONDS);
            return false;
        } catch (TimeoutException e) {
            return true;
        } catch (Exception e) {
            throw Runtime.throwChecked(e);
        }
    }

    @Override
    protected void describeMismatchSafely(Fiber<? extends A> item, Description mismatchDescription) {
        mismatchDescription.appendText("fiber completed with result ").appendValue(result);
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("fiber to run for at least " + timeout.toString() + " without yielding result");
    }

    public static <A> FiberTimeoutMatcher<A> timesOutAfter(Scheduler scheduler,
                                                           Canceller canceller,
                                                           Duration duration) {
        return new FiberTimeoutMatcher<>(scheduler, canceller, duration);
    }

    public static <A> FiberTimeoutMatcher<A> timesOutAfter(Scheduler scheduler, Duration duration) {
        return timesOutAfter(scheduler, canceller(), duration);
    }

    public static <A> FiberTimeoutMatcher<A> timesOutAfter(Canceller canceller, Duration duration) {
        return timesOutAfter(sameThreadScheduler(), canceller, duration);
    }

    public static <A> FiberTimeoutMatcher<A> timesOutAfter(Duration duration) {
        return timesOutAfter(canceller(), duration);
    }
}
