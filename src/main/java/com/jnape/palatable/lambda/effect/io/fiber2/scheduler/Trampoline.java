package com.jnape.palatable.lambda.effect.io.fiber2.scheduler;

import com.jnape.palatable.lambda.effect.io.fiber2.old.CancelToken;
import com.jnape.palatable.lambda.effect.io.fiber2.old.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public CancelToken delay(Duration delay, Runnable runnable) {
        long scheduled = System.nanoTime() + delay.toNanos();
        if (delay.isZero()) {
            schedule(runnable);
            return () -> {};
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        schedule(new Runnable() {
            @Override
            public void run() {
                if (!cancelled.get()) {
                    if (System.nanoTime() > scheduled)
                        runnable.run();
                    else
                        schedule(this);
                }
            }
        });
        return () -> cancelled.set(true);
    }

    public static Trampoline trampoline() {
        return new Trampoline(new ArrayBlockingQueue<>(1024), false);
    }
}
