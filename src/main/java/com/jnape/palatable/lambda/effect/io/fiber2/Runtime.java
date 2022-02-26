package com.jnape.palatable.lambda.effect.io.fiber2;

import com.jnape.palatable.lambda.effect.io.fiber2.old.Scheduler;
import com.jnape.palatable.lambda.runtime.fiber.Canceller;

public interface Runtime {

    Scheduler scheduler();

    Canceller cancel();

    Runtime SHARED_JVM = new Runtime() {
        @Override
        public Scheduler scheduler() {
            return Scheduler.shared();
        }

        @Override
        public Canceller cancel() {
            return Canceller.root();
        }
    };
}
