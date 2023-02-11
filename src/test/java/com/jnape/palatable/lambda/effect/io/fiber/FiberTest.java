package com.jnape.palatable.lambda.effect.io.fiber;


import com.jnape.palatable.lambda.adt.Unit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.fiber;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.race;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.result;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.succeeded;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.failure;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FiberTest {

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