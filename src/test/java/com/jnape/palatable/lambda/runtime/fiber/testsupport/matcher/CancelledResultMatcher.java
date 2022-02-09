package com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.runtime.fiber.Result;
import com.jnape.palatable.lambda.runtime.fiber.Result.Failed;
import com.jnape.palatable.lambda.runtime.fiber.Result.Successful;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public final class CancelledResultMatcher<A> extends TypeSafeDiagnosingMatcher<Result<A>> {

    public static final CancelledResultMatcher<?> INSTANCE = new CancelledResultMatcher<>();

    private CancelledResultMatcher() {
    }

    @Override
    protected boolean matchesSafely(Result<A> result, Description description) {
        if (result instanceof Result.Cancelled<?>) {
            return true;
        }

        if (result instanceof Successful<A> success) {
            description.appendText("successful result was ").appendValue(success.value());
        } else {
            description.appendText("failed with reason ").appendValue(((Failed<?>) result).reason());
        }
        return false;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("is cancelled");
    }

    @SuppressWarnings("unchecked")
    public static <A> CancelledResultMatcher<A> isCancelled() {
        return (CancelledResultMatcher<A>) INSTANCE;
    }
}