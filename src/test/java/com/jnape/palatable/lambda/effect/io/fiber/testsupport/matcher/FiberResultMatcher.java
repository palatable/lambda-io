package com.jnape.palatable.lambda.effect.io.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.effect.io.fiber.Canceller;
import com.jnape.palatable.lambda.effect.io.fiber.Environment;
import com.jnape.palatable.lambda.effect.io.fiber.Fiber;
import com.jnape.palatable.lambda.effect.io.fiber.Result;
import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import com.jnape.palatable.lambda.internal.Runtime;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static com.jnape.palatable.lambda.effect.io.fiber.Canceller.canceller;
import static com.jnape.palatable.lambda.effect.io.fiber.FiberRunLoop.fiberRunLoop;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static com.jnape.palatable.lambda.effect.io.fiber.RuntimeSettings.DEFAULT;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler.SameThread.sameThread;
import static org.hamcrest.Matchers.equalTo;

public final class FiberResultMatcher<A> extends TypeSafeMatcher<Fiber<A>> {

    private final Executor                   executor;
    private final Scheduler                  scheduler;
    private final Canceller                  canceller;
    private final Matcher<? super Result<A>> resultMatcher;

    private CompletableFuture<Result<? extends A>> result;

    private FiberResultMatcher(Executor executor, Scheduler scheduler, Canceller canceller,
                               Matcher<? super Result<A>> resultMatcher) {
        this.executor      = executor;
        this.scheduler     = scheduler;
        this.canceller     = canceller;
        this.resultMatcher = resultMatcher;
    }

    @Override
    protected synchronized boolean matchesSafely(Fiber<A> fiber) {
        if (result == null)
            result = new CompletableFuture<>() {{
                fiberRunLoop(new Environment(scheduler, executor, executor, () -> canceller), DEFAULT)
                        .unsafeRunAsync(fiber, res -> {
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

    public static <A> FiberResultMatcher<A> yieldsResult(Executor executor,
                                                         Scheduler scheduler,
                                                         Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return new FiberResultMatcher<>(executor, scheduler, canceller, resultMatcher);
    }


    public static <A> FiberResultMatcher<A> yieldsResult(Executor executor,
                                                         Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(executor, sameThread(), canceller, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Scheduler scheduler,
                                                         Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(sameThread(), scheduler, canceller, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Executor executor,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(executor, canceller(), resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Scheduler scheduler,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult(scheduler, canceller(), resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Canceller canceller,
                                                         Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult((Executor) sameThread(), canceller, resultMatcher);
    }

    public static <A> FiberResultMatcher<A> yieldsResult(Matcher<? super Result<A>> resultMatcher) {
        return yieldsResult((Executor) sameThread(), canceller(), resultMatcher);
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
