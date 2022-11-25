package research.fiber.benchmark;

import research.lambda.effect.io.fiber.Fiber;
import research.lambda.runtime.fiber.Canceller;
import com.jnape.palatable.lambda.runtime.fiber.Result;

import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.runtime.fiber.Scheduler.scheduledExecutorService;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static research.lambda.effect.io.fiber.Fiber.fiber;
import static research.lambda.effect.io.fiber.Fiber.forever;
import static research.lambda.runtime.fiber.scheduler.Trampoline.trampoline;

public class FiberBenchmark {
    private static final Sample              SAMPLE   = Sample.sample("native fiber", 100_000_000L, MICROSECONDS);
    private static final Consumer<Result<?>> CALLBACK = System.out::println;
    private static final Fiber<Object>       FOREVER  = forever(fiber(SAMPLE::mark));

    public static final class Trampolined {
        public static void main(String[] args) {
            FOREVER.execute(trampoline(), Canceller.root(), CALLBACK);
        }
    }

    public static final class Forked_SingleThreaded {
        public static void main(String[] args) {
            FOREVER.execute(scheduledExecutorService(Executors.newSingleThreadScheduledExecutor()), Canceller.root(), __ -> {});
        }
    }

    public static final class Forked_FixedMultiThreadedPool {
        public static void main(String[] args) {
            FOREVER.execute(scheduledExecutorService(Executors.newScheduledThreadPool(3)), Canceller.root(), __ -> {});
        }
    }

    public static final class Forked_ElasticPool {
        public static void main(String[] args) {
            FOREVER.execute(scheduledExecutorService(Executors.newScheduledThreadPool(0)), Canceller.root(), __ -> {});
        }
    }
}
