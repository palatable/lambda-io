package research.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.effect.io.fiber.Canceller;
import com.jnape.palatable.lambda.effect.io.fiber.Result;
import com.jnape.palatable.lambda.effect.io.fiber.Scheduler;
import research.lambda.runtime.fiber.internal.Array;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public interface Fiber<A> {

    void execute(Scheduler scheduler, Canceller cancel, Consumer<? super Result<A>> callback);

    @SafeVarargs
    static <A> Fiber<Array<A>> parallel(Fiber<A>... fibers) {
        return (s, c, k) -> s.schedule(() -> {
            if (c.cancelled()) {
                k.accept(Result.cancellation());
            } else {
                Object[]      results   = new Object[fibers.length];
                Canceller     shared    = c.addChild();
                AtomicInteger remaining = new AtomicInteger(fibers.length);
                for (int i = 0; i < fibers.length; i++) {
                    int finalI = i;
                    s.schedule(() -> fibers[finalI].execute(s, shared, res -> {
                        if (res instanceof Result.Cancellation<?>) {
                            if (remaining.getAndSet(-1) > 0) k.accept(Result.cancellation());
                        } else if (res instanceof Result.Success<A> success) {
                            results[finalI] = success.value();
                            if (c.cancelled() && remaining.getAndSet(-1) > 0) k.accept(Result.cancellation());
                            else if (remaining.decrementAndGet() == 0) {
                                if (c.cancelled()) k.accept(Result.cancellation());
                                else {
                                    @SuppressWarnings("unchecked") Array<A> array = (Array<A>) Array.shallowCopy(results);
                                    k.accept(Result.success(array));
                                }
                            }
                        } else {
                            Result.Failure<A> failure = (Result.Failure<A>) res;
                            if (remaining.getAndSet(-1) > 0) {
                                shared.cancel();
                                k.accept(c.cancelled() ? Result.cancellation() : failure.contort());
                            }
                        }
                    }));
                }
            }
        });
    }

}

