package com.jnape.palatable.lambda.effect.io.fiber;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.cancelled;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.failed;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.succeeded;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.cancellation;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static com.jnape.palatable.lambda.effect.io.fiber.Trampoline.trampoline;
import static com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TrampolineTest {

    private static final RuntimeException CAUSE = new RuntimeException("kaboom");

    private static final Trampoline TRAMPOLINE = trampoline(sameThreadScheduler());

    @Test
    public void value() {
        assertFiber(succeeded(1), success(1));
        assertFiber(failed(CAUSE), failure(CAUSE));
        assertFiber(cancelled(), cancellation());
    }

    @Test
    public void callback() {
        for (Result<Integer> expected : List.<Result<Integer>>of(success(1), failure(CAUSE), cancellation())) {
            assertFiber(fiber(k -> k.accept(expected)), expected);
        }
    }

    @Test
    public void supplier() {
        assertFiber(fiber(() -> 1), success(1));
        assertFiber(fiber((Supplier<?>) () -> {throw CAUSE;}), failure(CAUSE));
    }

    @Test
    public void runnable() {
        assertFiber(fiber(() -> {}), success());
        assertFiber(fiber((Runnable) () -> {throw CAUSE;}), failure(CAUSE));
    }



    private static <A> void assertFiber(Fiber<A> fiber, Result<A> expected) {
        assertEquals(expected, new CompletableFuture<Result<A>>() {{
            TRAMPOLINE.unsafeRunAsync(fiber, this::complete);
        }}.join());
    }
}