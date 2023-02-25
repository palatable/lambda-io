package com.jnape.palatable.lambda.effect.io.fiber;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.jnape.palatable.lambda.adt.Maybe.just;
import static com.jnape.palatable.lambda.adt.Maybe.nothing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class RuntimeTest {

    @Nested
    public class JVMTest {

        @Test
        public void defaultJvmRuntimeIsSystemRunLoop() {
            synchronized (Runtime.JVM.class) {
                assertSame(FiberRunLoop.system(), Runtime.JVM.get());
            }
        }

        @Test
        public void setReturnsPrevious() {
            synchronized (Runtime.JVM.class) {
                Runtime original = Runtime.JVM.get();
                assertEquals(just(original), Runtime.JVM.set(null));
                assertEquals(nothing(), Runtime.JVM.set(original));
                assertSame(original, Runtime.JVM.get());
            }
        }
    }


}