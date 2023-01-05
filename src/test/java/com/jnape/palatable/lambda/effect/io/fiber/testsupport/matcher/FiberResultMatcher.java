package com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.effect.io.fiber.Canceller;
import com.jnape.palatable.lambda.effect.io.fiber.Fiber;
import com.jnape.palatable.lambda.effect.io.fiber.Result;
import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import com.jnape.palatable.lambda.effect.io.fiber.Timer;
import com.jnape.palatable.lambda.internal.Runtime;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.concurrent.CompletableFuture;

import static com.jnape.palatable.lambda.effect.io.fiber.Canceller.canceller;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static com.jnape.palatable.lambda.effect.io.fiber.Trampoline.trampoline;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler.SameThreadTimer.sameThreadTimer;
import static org.hamcrest.Matchers.equalTo;

public final class FiberResultMatcher<A> extends TypeSafeMatcher<Fiber<A>> {

    private final Scheduler                  scheduler;
    private final Timer                      timer;
    private final Canceller                  canceller;
    private final Matcher<? super Result<A>> resultMatcher;

    private CompletableFuture<Result<? extends A>> result;

    private FiberResultMatcher(Scheduler scheduler, Timer timer, Canceller canceller,
                               Matcher<? super Result<A>> resultMatcher) {
        this.scheduler     = scheduler;
        this.timer         = timer;
        this.canceller     = canceller;
        this.resultMatcher = resultMatcher;
    }

    @Override
    protected synchronized boolean matchesSafely(Fiber<A> fiber) {
        if (result == null)
            result = new CompletableFuture<>() {{
                trampoline(() -> canceller, scheduler, timer).unsafeRunAsync(fiber, res -> {
                    if (!complete(res))
                        throw new AssertionError("Fiber <" + fiber + "> completed again with result <" + res + ">");
                });
            }};

        try {
            return resultMatcher.matches(result.get());
        } catch (Exception e) {
            throw Runtime.throwChecked(e);
        }
    }

    @Override
    protected void describeMismatchSafely(Fiber<A> item, Description mismatchDescription) {
        mismatchDescription.appendText("was ");
        mismatchDescription.appendValue(result.join());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("fiber result of ");
        description.appendDescriptionOf(resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Scheduler scheduler,
                                                         Timer timer,
                                                         Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return new FiberResultMatcher<>(scheduler, timer, canceller, resultMatcher);
    }


    public static <A> FiberResultMatcher<A> yieldsResult(Scheduler scheduler,
                                                         Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(scheduler, sameThreadTimer(), canceller, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Timer timer,
                                                         Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(sameThreadScheduler(), timer, canceller, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Scheduler scheduler,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(scheduler, canceller(), resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Timer timer,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(timer, canceller(), resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(sameThreadScheduler(), canceller, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(sameThreadScheduler(), canceller(), resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Result<A> result) {
        return yieldsResult(equalTo(result));
    }

    public static <A> FiberResultMatcher<A> yieldsResult(A result) {
        return yieldsResult(success(result));
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Throwable cause) {
        return yieldsResult(failure(cause));
    }
}
