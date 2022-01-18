package com.jnape.palatable.lambda.effect.io.fiber2;

import com.jnape.palatable.lambda.effect.io.fiber2.old.Cancel;
import com.jnape.palatable.lambda.effect.io.fiber2.old.Scheduler;

public interface Runtime {

    Scheduler scheduler();

    Cancel cancel();

    Runtime SHARED_JVM = new Runtime() {
        @Override
        public Scheduler scheduler() {
            return Scheduler.shared();
        }

        @Override
        public Cancel cancel() {
            return Cancel.root();
        }
    };
}
