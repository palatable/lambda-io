package com.jnape.palatable.lambda.benchmark;

import java.util.concurrent.TimeUnit;

public final class Sample {
    private final long   startNanos;
    private final long   reportModulus;
    private final long   timeResolutionCoefficient;
    private final String timeUnit;

    private long count;

    private Sample(long startNanos, long reportModulus, TimeUnit timeUnit, long count) {
        this.startNanos           = startNanos;
        this.reportModulus        = reportModulus;
        this.count                = count;
        this.timeUnit             = switch (timeUnit) {
            case NANOSECONDS -> "ns";
            case MICROSECONDS -> "us";
            case MILLISECONDS -> "ms";
            case SECONDS -> "s";
            case MINUTES -> "m";
            case HOURS -> "h";
            case DAYS -> "d";
        };
        timeResolutionCoefficient = timeUnit.toNanos(1);
    }

    public void mark() {
        if (count++ % reportModulus == 0) {
            long elapsedNanos = System.nanoTime() - startNanos;
            long sample       = count - 1;
            long elapsedTime  = elapsedNanos / timeResolutionCoefficient;
            System.out.format("[%s] Sample: %d (%f/%s average, %d%s elapsed)",
                              Thread.currentThread().getName(),
                              sample,
                              ((float) sample) / elapsedTime,
                              timeUnit,
                              elapsedTime,
                              timeUnit)
                    .println();
        }
    }

    public static Sample sample(long reportModulus, TimeUnit timeUnit) {
        return new Sample(System.nanoTime(), reportModulus, timeUnit, 0);
    }
}
