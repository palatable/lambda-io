package com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.internal.Runtime;
import com.jnape.palatable.lambda.runtime.fiber.Fiber;
import com.jnape.palatable.lambda.runtime.fiber.FiberTest;
import com.jnape.palatable.lambda.runtime.fiber.Result;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static com.jnape.palatable.lambda.runtime.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class FiberTimeoutMatcher<A> extends TypeSafeMatcher<Fiber<? extends A>> {

    private final Duration            timeout;
    private       Result<? extends A> result;

    private FiberTimeoutMatcher(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    protected boolean matchesSafely(Fiber<? extends A> fiber) {
        try {
            result = new CompletableFuture<Result<? extends A>>() {{
                fiber.execute(sameThreadScheduler(), this::complete);
            }}.get(timeout.toMillis(), MILLISECONDS);
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
        description.appendText("fiber to run for at least " + timeout.toString());
    }

    public static <A> FiberTimeoutMatcher<A> timesOutAfter(Duration duration) {
        return new FiberTimeoutMatcher<>(duration);
    }
}
