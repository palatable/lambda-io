package com.jnape.palatable.lambda.runtime.fiber.testsupport.scheduler;

import com.jnape.palatable.lambda.runtime.fiber.Scheduler;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

public final class SameThreadScheduler implements Scheduler {

    private static final SameThreadScheduler INSTANCE = new SameThreadScheduler(new ArrayBlockingQueue<>(1024), false);

    private final ArrayBlockingQueue<Runnable> tasks;
    private       boolean                      running;

    private SameThreadScheduler(ArrayBlockingQueue<Runnable> tasks, boolean running) {
        this.tasks   = tasks;
        this.running = running;
    }

    @Override
    public void schedule(Runnable runnable) {
        tasks.add(runnable);
        if (!running)
            start();
    }

    private void start() {
        int n = 1024;
        new ArrayList<Runnable>(n) {{
            running = true;
            while (tasks.drainTo(this, n) > 0) {
                forEach(Runnable::run);
                clear();
            }
            running = false;
        }};
    }

    public static SameThreadScheduler sameThreadScheduler() {
        return INSTANCE;
    }
}
