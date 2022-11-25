package com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.runtime.fiber.Fiber;
import com.jnape.palatable.lambda.runtime.fiber.Result;
import com.jnape.palatable.lambda.runtime.fiber.Scheduler;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.concurrent.CompletableFuture;

import static com.jnape.palatable.lambda.runtime.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static org.hamcrest.Matchers.equalTo;

public final class FiberResultMatcher<A> extends TypeSafeDiagnosingMatcher<Fiber<A>> {

    private static final Scheduler THROWING_SCHEDULER = runnable -> {
        throw new UnsupportedOperationException("Expected fiber to yield pure result," +
                                                        " but it attempted to execute on the Scheduler");
    };

    private final Scheduler                  scheduler;
    private final Matcher<? super Result<A>> resultMatcher;

    private FiberResultMatcher(Scheduler scheduler, Matcher<? super Result<A>> resultMatcher) {
        this.scheduler     = scheduler;
        this.resultMatcher = resultMatcher;
    }

    @Override
    protected boolean matchesSafely(Fiber<A> fiber, Description description) {
        return new CompletableFuture<Boolean>() {{
            fiber.execute(scheduler, res -> {
                description.appendText("fiber ");
                resultMatcher.describeMismatch(res, description);
                complete(resultMatcher.matches(res));
            });
        }}.join();
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("fiber that ");
        description.appendDescriptionOf(resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Scheduler scheduler,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return new FiberResultMatcher<>(scheduler, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(sameThreadScheduler(), resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsPureResult(Result<? extends A> result) {
        return yieldsResult(THROWING_SCHEDULER, equalTo(result));
    }
}
