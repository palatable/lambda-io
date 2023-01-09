package com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler;

import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import com.jnape.palatable.lambda.effect.io.fiber.Timer;

import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

import static java.util.Comparator.comparing;

public final class SameThread implements Timer, Scheduler {

    private static final SameThread INSTANCE = new SameThread(
            new PriorityQueue<>(comparing(Task::executionTick).thenComparing(Task::index)),
            false,
            0,
            0);

    private final PriorityQueue<Task> tasks;

    private boolean running;
    private long    currentTick;
    private long    currentIndex;

    private SameThread(PriorityQueue<Task> tasks, boolean running, long currentTick, long currentIndex) {
        this.tasks        = tasks;
        this.running      = running;
        this.currentTick  = currentTick;
        this.currentIndex = currentIndex;
    }

    @Override
    public void schedule(Runnable runnable) {
        delay(runnable, 0, TimeUnit.SECONDS);
    }

    @Override
    public Runnable delay(Runnable runnable, long delay, TimeUnit timeUnit) {
        if (delay < 0)
            throw new IllegalStateException("delay cannot be negative");

        Task task = new Task(runnable, currentTick + timeUnit.toNanos(delay), currentIndex++);
        tasks.add(task);

        if (!running)
            start();

        return () -> tasks.remove(task);
    }

    private void start() {
        running = true;
        Task head;
        try {
            while ((head = tasks.poll()) != null) {
                currentTick = head.executionTick();
                head.runnable().run();
            }
        } finally {
            running = false;
        }
    }

    public static SameThread sameThread() {
        return INSTANCE;
    }

    private record Task(Runnable runnable, long executionTick, long index) {
    }
}
