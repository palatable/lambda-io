package com.jnape.palatable.lambda.effect.io.fiber;


import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.result;
import static com.jnape.palatable.lambda.effect.io.fiber.Fiber.succeeded;
import static com.jnape.palatable.lambda.effect.io.fiber.Result.success;
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
}