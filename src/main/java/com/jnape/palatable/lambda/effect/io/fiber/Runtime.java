package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Maybe;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Maybe.maybe;

public interface Runtime {

    <A> void schedule(Fiber<A> fiber, Consumer<? super Result<A>> callback);

    final class JVM {
        private static final AtomicReference<Runtime> GLOBAL = new AtomicReference<>();

        private JVM() {
        }

        public static Maybe<Runtime> set(Runtime runtime) {
            return maybe(GLOBAL.getAndSet(runtime));
        }

        public static Runtime get() {
            return GLOBAL.updateAndGet(r -> r == null ? FiberRunLoop.system() : r);
        }
    }
}

