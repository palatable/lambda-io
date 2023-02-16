package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public interface Runtime {

    <A> void schedule(Fiber<A> fiber, Consumer<? super Result<A>> callback);

    final class JVM {
        private static final AtomicReference<Runtime> GLOBAL = new AtomicReference<>();

        private JVM() {
        }

        public static void set(Runtime runtime) {
            GLOBAL.set(runtime);
        }

        public static Runtime get() {
            return GLOBAL.updateAndGet(r -> r == null ? FiberRunLoop.system() : r);
        }
    }
}

