package com.jnape.palatable.lambda.effect.io.fiber;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ResultTest {

    @Nested
    public final class Success {

        @Test
        public void singletonSuccessUnit() {
            assertSame(Result.success(), Result.success());
        }

        @Test
        public void equality() {
            assertEquals(Result.success(1), Result.success(1));
            assertNotEquals(Result.success(1), Result.success(2));
        }

        @Test
        public void hashing() {
            assertEquals(Result.success(1).hashCode(), Result.success(1).hashCode());
            assertNotEquals(Result.success(1).hashCode(), Result.success(2).hashCode());
        }

        @Test
        public void friendlyToString() {
            assertEquals("Success[value=1]", Result.success(1).toString());
        }
    }

    @Nested
    public final class Failure {
        private static final Throwable t1 = new RuntimeException("foo");
        private static final Throwable t2 = new RuntimeException("bar");

        @Test
        public void equality() {
            assertEquals(Result.failure(t1), Result.failure(t1));
            assertNotEquals(Result.failure(t1), Result.failure(t2));
        }

        @Test
        public void hashing() {
            assertEquals(Result.failure(t1).hashCode(), Result.failure(t1).hashCode());
            assertNotEquals(Result.failure(t1).hashCode(), Result.failure(t2).hashCode());
        }

        @Test
        @SuppressWarnings("AssertBetweenInconvertibleTypes")
        public void contortion() {
            Result.Failure<Integer> intFailure    = Result.failure(t1);
            Result.Failure<String>  stringFailure = intFailure.contort();
            assertSame(intFailure, stringFailure);
        }

        @Test
        public void friendlyToString() {
            assertEquals("Failure[reason=" + t1 + "]", Result.failure(t1).toString());
        }
    }

    @Nested
    public final class Cancellation {

        @Test
        public void singleton() {
            assertSame(Result.cancellation(), Result.cancellation());
        }

        @Test
        public void friendlyToString() {
            assertEquals("Cancellation[]", Result.cancellation().toString());
        }
    }
}