package com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.runtime.fiber.Canceller;
import com.jnape.palatable.lambda.runtime.fiber.Fiber;
import com.jnape.palatable.lambda.runtime.fiber.Result;
import com.jnape.palatable.lambda.runtime.fiber.Scheduler;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.concurrent.CompletableFuture;

import static com.jnape.palatable.lambda.runtime.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static org.hamcrest.Matchers.equalTo;

public final class FiberResultMatcher<A> extends TypeSafeMatcher<Fiber<A>> {

    private static final Scheduler THROWING_SCHEDULER = runnable -> {
        throw new UnsupportedOperationException("Expected fiber to yield pure result," +
                                                        " but it attempted to execute on the Scheduler");
    };

    private final Scheduler                              scheduler;
    private final Matcher<? super Result<A>>             resultMatcher;
    private final Canceller                              canceller;
    private       CompletableFuture<Result<? extends A>> result;

    private FiberResultMatcher(Scheduler scheduler, Canceller canceller, Matcher<? super Result<A>> resultMatcher) {
        this.scheduler     = scheduler;
        this.canceller     = canceller;
        this.resultMatcher = resultMatcher;
    }

    @Override
    protected boolean matchesSafely(Fiber<A> fiber) {
        if (result == null)
            result = new CompletableFuture<>() {{
                fiber.execute(scheduler, canceller, this::complete);
            }};

        return resultMatcher.matches(result.join());
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
                                                         Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return new FiberResultMatcher<>(scheduler, canceller, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Scheduler scheduler,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(scheduler, Canceller.canceller(), resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(sameThreadScheduler(), canceller, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(Canceller.canceller(), resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsPureResult(Matcher<Result<? extends A>> resultMatcher) {
        Canceller cancelledCanceller = Canceller.canceller();
        cancelledCanceller.cancel();
        return yieldsResult(THROWING_SCHEDULER, cancelledCanceller, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsPureResult(Result<? extends A> result) {
        return yieldsPureResult(equalTo(result));
    }
}
