package research.fiber.benchmark;

import com.jnape.palatable.lambda.runtime.fiber.Result;
import com.jnape.palatable.lambda.runtime.fiber.benchmark.Sample;
import research.lambda.effect.io.fiber.Fiber;
import research.lambda.runtime.fiber.Canceller;

import java.util.function.Consumer;

import static com.jnape.palatable.lambda.runtime.fiber.benchmark.Sample.sample;
import static com.jnape.palatable.lambda.runtime.fiber.testsupport.scheduler.SameThreadScheduler.sameThreadScheduler;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static research.lambda.effect.io.fiber.Fiber.fiber;
import static research.lambda.effect.io.fiber.Fiber.forever;

public class FiberBenchmark {
    private static final Sample              SAMPLE   = sample("native fiber", 100_000_000L, MICROSECONDS);
    private static final Consumer<Result<?>> CALLBACK = System.out::println;
    private static final Fiber<?>            FOREVER  = forever(fiber(SAMPLE::mark));

    public static final class Trampolined {
        public static void main(String[] args) {
            FOREVER.execute(sameThreadScheduler(), Canceller.root(), CALLBACK);
        }
    }

    public static final class Forked_SingleThreaded {
        public static void main(String[] args) {
            FOREVER.execute(newSingleThreadScheduledExecutor()::execute, Canceller.root(), CALLBACK);
        }
    }

    public static final class Forked_FixedMultiThreadedPool {
        public static void main(String[] args) {
            FOREVER.execute(newScheduledThreadPool(3)::execute, Canceller.root(), CALLBACK);
        }
    }

    public static final class Forked_ElasticPool {
        public static void main(String[] args) {
            FOREVER.execute(newScheduledThreadPool(0)::execute, Canceller.root(), CALLBACK);
        }
    }
}
