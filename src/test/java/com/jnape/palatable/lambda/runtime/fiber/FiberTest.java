package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn1;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.functions.Fn2.curried;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Constantly.constantly;
import static com.jnape.palatable.lambda.functions.builtin.fn2.Eq.eq;
import static com.jnape.palatable.lambda.functions.builtin.fn3.Times.times;
import static com.jnape.palatable.lambda.functions.builtin.fn4.IfThenElse.ifThenElse;
import static com.jnape.palatable.lambda.runtime.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.runtime.fiber.scheduler.testsupport.ExplodingScheduler.explodingScheduler;
import static com.jnape.palatable.lambda.runtime.fiber.scheduler.testsupport.SameThreadScheduler.sameThreadScheduler;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.Tags.STACK_SAFETY;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.CancelledResultMatcher.isCancelled;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FailedResultMatcher.failsWith;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.FiberMatcher.whenExecuted;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.SuccessfulResultMatcher.succeeds;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.SuccessfulResultMatcher.succeedsWith;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.matcher.SuccessfulResultMatcher.successfully;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

public class FiberTest {}