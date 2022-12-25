package com.jnape.palatable.lambda.effect.io.fiber;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static com.jnape.palatable.lambda.effect.io.fiber.Scheduler.scheduler;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SchedulerTest {

    @Nested
    public class FromExecutor {

        @Test
        public void delegatesToBackingExecutor() {
            AtomicReference<Runnable> ref       = new AtomicReference<>();
            Scheduler                 scheduler = scheduler(ref::set);
            assertNull(ref.get());
            Runnable runnable = () -> {};
            scheduler.schedule(runnable);
            assertEquals(runnable, ref.get());
        }
    }
}