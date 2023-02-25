package com.jnape.palatable.lambda.effect.io.fiber;


import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.adt.hlist.Tuple2;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.adt.Try.trying;
import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.adt.hlist.HList.tuple;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.cancelled;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.failed;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.race;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.result;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.succeeded;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FiberTest {

    @Test
    public void unsafeAsyncUsesJvmRuntime() {
        Fiber<Unit>                    fiber    = succeeded();
        Consumer<? super Result<Unit>> callback = res -> {};

        AtomicReference<Tuple2<Fiber<?>, Consumer<?>>> scheduled = new AtomicReference<>();
        withRuntime(new Runtime() {
            @Override
            public <A> void schedule(Fiber<A> fiber, Consumer<? super Result<A>> callback) {
                scheduled.set(tuple(fiber, callback));
            }
        }, () -> fiber.unsafeRunAsync(callback));

        assertEquals(tuple(fiber, callback), scheduled.get());
    }

    @Test
    public void unsafeSyncNormalizesResult() {
        assertEquals(UNIT, succeeded().unsafeRunSync());
        assertThrows(CancellationException.class, () -> cancelled().unsafeRunSync());
        assertThrows(IllegalStateException.class, () -> failed(new IllegalStateException()).unsafeRunSync());
    }

    private static void withRuntime(Runtime runtime, Runnable scope) {
        synchronized (Runtime.JVM.class) {
            Runtime old = Runtime.JVM.set(runtime).orElse(null);
            trying(scope::run).ensuring(() -> Runtime.JVM.set(old));
        }
    }

    @Nested
    public class Value {

        @Test
        public void succeededUnitAvoidsAllocation() {
            assertSame(succeeded(), succeeded(UNIT));
        }

        @Test
        public void resultSuccessUnitAvoidsAllocation() {
            assertSame(result(success(UNIT)), succeeded());
        }
    }

    @Nested
    public class Suspension {

        @Test
        public void supplierIsWrappedInTryCatch() {
            IllegalStateException exception = new IllegalStateException("kaboom");

            AtomicReference<Result<Unit>> ref = new AtomicReference<>();
            ((com.jnape.palatable.lambda.effect.io.fiber.Suspension<Unit>)
                    fiber((Supplier<Unit>) () -> {throw exception;})).k().accept((Consumer<Result<Unit>>) ref::set);

            assertEquals(failure(exception), ref.get());

        }
    }

    @Nested
    public class Race {

        @Test
        public void preservesContestantOrder() {
            assertEquals(new ArrayList<>(asList(succeeded(1), succeeded(2), succeeded(3), succeeded(4))),
                         ((com.jnape.palatable.lambda.effect.io.fiber.Race<Integer>)
                                 race(succeeded(1), succeeded(2), succeeded(3), succeeded(4)))
                                 .fibers());

        }
    }
}