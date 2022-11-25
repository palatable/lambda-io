package research.fiber.scheduler.testsupport;

import com.jnape.palatable.lambda.runtime.fiber.Scheduler;

public final class ExplodingScheduler implements Scheduler {
    private static final ExplodingScheduler INSTANCE = new ExplodingScheduler();

    private ExplodingScheduler() {
    }

    @Override
    public void schedule(Runnable runnable) {
        throw new UnsupportedOperationException("schedule(Runnable)");
    }

    public static ExplodingScheduler explodingScheduler() {
        return INSTANCE;
    }
}
