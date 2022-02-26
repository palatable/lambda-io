package com.jnape.palatable.lambda.runtime.fiber.scheduler;

import com.jnape.palatable.lambda.runtime.fiber.Canceller;
import com.jnape.palatable.lambda.runtime.fiber.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;

public final class Trampoline implements Scheduler {
    private final ArrayBlockingQueue<Runnable> tasks;
    private       boolean                      running;

    private Trampoline(ArrayBlockingQueue<Runnable> tasks, boolean running) {
        this.tasks   = tasks;
        this.running = running;
    }

    @Override
    public void schedule(Runnable task) {
        tasks.add(task);
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

    @Override
    public void schedule(Duration delay, Runnable runnable, Canceller canceldler) {
        long scheduled = System.nanoTime() + delay.toNanos();
        if (delay.isZero()) {
            schedule(runnable);
            return;
        }
        schedule(new Runnable() {
            @Override
            public void run() {
                if (System.nanoTime() > scheduled)
                    runnable.run();
                else
                    schedule(this);
            }
        });
    }

    public static Trampoline trampoline() {
        return new Trampoline(new ArrayBlockingQueue<>(1024), false);
    }
}
