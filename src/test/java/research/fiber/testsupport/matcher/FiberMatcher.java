package research.fiber.testsupport.matcher;

import research.lambda.effect.io.fiber.Fiber;
import research.lambda.runtime.fiber.Canceller;
import research.lambda.runtime.fiber.Result;
import research.lambda.runtime.fiber.Scheduler;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.concurrent.CompletableFuture;

import static research.fiber.scheduler.testsupport.SameThreadScheduler.sameThreadScheduler;

public final class FiberMatcher<A> extends TypeSafeDiagnosingMatcher<Fiber<A>> {

    private final Scheduler                  scheduler;
    private final Canceller                  canceller;
    private final Matcher<? super Result<A>> resultMatcher;

    private FiberMatcher(Scheduler scheduler,
                         Canceller canceller,
                         Matcher<? super Result<A>> resultMatcher) {
        this.scheduler     = scheduler;
        this.canceller     = canceller;
        this.resultMatcher = resultMatcher;
    }

    @Override
    protected boolean matchesSafely(Fiber<A> fiber, Description description) {
        return new CompletableFuture<Boolean>() {{
            fiber.execute(scheduler, canceller, res -> {
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

    public static <A> FiberMatcher<A> whenExecuted(Scheduler scheduler,
                                                   Canceller canceller,
                                                   Matcher<? super Result<A>> resultMatcher) {
        return new FiberMatcher<>(scheduler, canceller, resultMatcher);
    }

    public static <A> FiberMatcher<A> whenExecuted(Scheduler scheduler,
                                                   Matcher<? super Result<A>> resultMatcher) {
        return whenExecuted(scheduler, Canceller.root(), resultMatcher);
    }

    public static <A> FiberMatcher<A> whenExecuted(Matcher<? super Result<A>> resultMatcher) {
        return whenExecuted(sameThreadScheduler(), resultMatcher);
    }

    public static <A> FiberMatcher<A> whenExecuted(Canceller canceller,
                                                   Matcher<? super Result<A>> resultMatcher) {
        return whenExecuted(sameThreadScheduler(), canceller, resultMatcher);
    }
}
