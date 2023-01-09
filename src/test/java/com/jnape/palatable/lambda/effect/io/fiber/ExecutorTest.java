package com.jnape.palatable.lambda.effect.io.fiber;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ExecutorTest {

    @Nested
    public class FromExecutor {

        @Test
        public void delegatesToBackingExecutor() {
            AtomicReference<Runnable> ref      = new AtomicReference<>();
            Executor                  executor = ref::set;
            assertNull(ref.get());
            Runnable runnable = () -> {};
            executor.execute(runnable);
            assertEquals(runnable, ref.get());
        }
    }
}