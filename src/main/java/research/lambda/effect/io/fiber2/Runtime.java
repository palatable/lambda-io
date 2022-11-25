package research.lambda.effect.io.fiber2;

import research.lambda.effect.io.fiber2.old.Scheduler;
import research.lambda.runtime.fiber.Canceller;

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
