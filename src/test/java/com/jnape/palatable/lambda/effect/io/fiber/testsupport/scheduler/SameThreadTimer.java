package com.jnape.palatable.lambda.effect.io.fiber.testsupport.scheduler;

import com.jnape.palatable.lambda.effect.io.fiber.Timer;

import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;

import static java.util.Comparator.comparing;

public final class SameThreadTimer implements Timer {

    private final PriorityQueue<Task> tasks;

    private boolean running;
    private long    currentTick;

    private SameThreadTimer(PriorityQueue<Task> tasks, boolean running, long currentTick) {
        this.tasks       = tasks;
        this.running     = running;
        this.currentTick = currentTick;
    }

    @Override
    public Runnable delay(Runnable runnable, long delay, TimeUnit timeUnit) {
        if (delay < 0)
            throw new IllegalStateException("delay cannot be negative");

        Task task = new Task(runnable, currentTick + timeUnit.toNanos(delay));
        tasks.add(task);

        if (!running)
            start();

        return () -> tasks.remove(task);
    }

    private void start() {
        running = true;
        Task head;
        while ((head = tasks.poll()) != null) {
            try {
                currentTick = head.executionTick();
                head.runnable().run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        running = false;
    }

    public static SameThreadTimer sameThreadTimer(long currentTick) {
        return new SameThreadTimer(new PriorityQueue<>(1024, comparing(Task::executionTick)), false, currentTick);
    }

    public static SameThreadTimer sameThreadTimer() {
        return sameThreadTimer(0);
    }

    private record Task(Runnable runnable, long executionTick) {
    }
}
