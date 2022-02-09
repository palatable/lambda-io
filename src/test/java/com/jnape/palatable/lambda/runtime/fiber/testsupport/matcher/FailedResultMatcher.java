package com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.runtime.fiber.Result;
import com.jnape.palatable.lambda.runtime.fiber.Result.Failed;
import com.jnape.palatable.lambda.runtime.fiber.Result.Successful;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.equalTo;

public final class FailedResultMatcher<A> extends TypeSafeDiagnosingMatcher<Result<A>> {

    private final Matcher<? super Throwable> delegate;

    private FailedResultMatcher(Matcher<? super Throwable> delegate) {
        this.delegate = delegate;
    }

    @Override
    protected boolean matchesSafely(Result<A> result, Description description) {
        if (result instanceof Failed<?> failed) {
            Throwable reason = failed.reason();
            delegate.describeMismatch(reason, description.appendText("failure reason "));
            return delegate.matches(reason);
        }

        if (result instanceof Successful<A> success) {
            delegate.describeMismatch(success.value(), description.appendText("successful result "));
        } else {
            description.appendText("was cancelled");
        }
        return false;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("fails with reason ").appendDescriptionOf(delegate);
    }


    public static <A> FailedResultMatcher<A> fails(Matcher<? super Throwable> delegate) {
        return new FailedResultMatcher<>(delegate);
    }

    public static <A> FailedResultMatcher<A> failsWith(Throwable reason) {
        return fails(equalTo(reason));
    }

    public static <A> FailedResultMatcher<A> fails() {
        return fails(anything());
    }
}
