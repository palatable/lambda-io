package com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.runtime.fiber.Fiber;
import com.jnape.palatable.lambda.runtime.fiber.Result;
import com.jnape.palatable.lambda.runtime.fiber.Scheduler;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.concurrent.CompletableFuture;

import static com.jnape.palatable.lambda.runtime.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;

public final class FiberMatcher<A> extends TypeSafeDiagnosingMatcher<Fiber<A>> {

    private final Scheduler                  scheduler;
    private final Matcher<? super Result<A>> resultMatcher;

    private FiberMatcher(Scheduler scheduler, Matcher<? super Result<A>> resultMatcher) {
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

    public static <A> FiberMatcher<A> whenExecuted(Scheduler scheduler, Matcher<? super Result<A>> resultMatcher) {
        return new FiberMatcher<>(scheduler, resultMatcher);
    }

    public static <A> FiberMatcher<A> whenExecuted(Matcher<? super Result<A>> resultMatcher) {
        return whenExecuted(sameThreadScheduler(), resultMatcher);
    }
}
