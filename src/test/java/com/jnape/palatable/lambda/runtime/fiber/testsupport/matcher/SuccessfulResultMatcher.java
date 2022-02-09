package com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher;

import com.jnape.palatable.lambda.runtime.fiber.Result;
import com.jnape.palatable.lambda.runtime.fiber.Result.Failed;
import com.jnape.palatable.lambda.runtime.fiber.Result.Successful;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.equalTo;

public final class SuccessfulResultMatcher<A> extends TypeSafeDiagnosingMatcher<Result<A>> {

    private final Matcher<? super A> delegate;

    private SuccessfulResultMatcher(Matcher<? super A> delegate) {
        this.delegate = delegate;
    }

    @Override
    protected boolean matchesSafely(Result<A> result, Description description) {
        if (result instanceof Successful<A> success) {
            A value = success.value();
            delegate.describeMismatch(value, description.appendText("successful result "));
            return delegate.matches(value);
        }

        if (result instanceof Failed<?> failed) {
            description.appendText("failed with reason ").appendValue(failed.reason());
        } else {
            description.appendText("was cancelled");
        }
        return false;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("succeeds with ").appendDescriptionOf(delegate);
    }


    public static <A> SuccessfulResultMatcher<A> successfully(Matcher<? super A> delegate) {
        return new SuccessfulResultMatcher<>(delegate);
    }

    public static <A> SuccessfulResultMatcher<A> succeedsWith(A expected) {
        return successfully(equalTo(expected));
    }

    public static <A> SuccessfulResultMatcher<A> succeeds() {
        return successfully(anything());
    }
}
