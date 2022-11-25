package research.lambda.effect.io;

import com.jnape.palatable.lambda.adt.Unit;
import research.lambda.effect.io.fiber2.old.FiberResult;
import research.lambda.effect.io.fiber2.old.FiberResult.Cancelled;
import research.lambda.effect.io.fiber2.old.FiberResult.Failure;
import research.lambda.effect.io.fiber2.old.Scheduler;
import research.lambda.effect.io.interpreter.TailExpr;
import research.lambda.effect.io.interpreter.TailExpr.Recur;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static research.lambda.effect.io.interpreter.RunSyncFiber.runSyncFiber;

public interface IOPlatform {

    <A> void unsafeRun(IO<A> io, Consumer<? super FiberResult<A>> k);

    default <A> A unsafeRun(IO<A> io) {
        return new CompletableFuture<A>() {{
            unsafeRun(io, resA -> {
                if (resA instanceof Cancelled<?>) {
                    cancel(false);
                } else if (resA instanceof Failure<?> failure) {
                    completeExceptionally(failure.ex());
                } else {
                    complete(((FiberResult.Success<A>) resA).result());
                }
            });
        }}.join();
    }

    static IOPlatform system() {
        return Constants.SYSTEM;
    }
}

interface Constants {
    IOPlatform SYSTEM = new IOPlatform() {
        private static final int WORK_UNITS_PER_QUANTA = 512;

        static final class StackCounter {
            public int x = 0;

            StackCounter reset() {
                x = 0;
                return this;
            }
        }

        private static <A> void trampoline(
                IO<A> io,
                Scheduler scheduler,
                Interpreter<A, TailExpr<IO<A>, Unit>> interpreter,
                StackCounter stackCounter) {
            TailExpr<IO<A>, Unit> expr = new Recur<>(io);
            while (expr instanceof Recur<IO<A>, Unit> i) {
                if (stackCounter.x++ < WORK_UNITS_PER_QUANTA)
                    expr = i.a().interpret(interpreter);
                else {
                    scheduler.schedule(() -> trampoline(i.a(), scheduler, interpreter, stackCounter.reset()));
                    return;
                }
            }
        }

        @Override
        public <A> void unsafeRun(IO<A> io, Consumer<? super FiberResult<A>> k) {
            trampoline(io, k, Scheduler.shared(), new StackCounter());
        }

        private <A> void trampoline(IO<A> io, Consumer<? super FiberResult<A>> k, Scheduler executor,
                                    StackCounter stackCounter) {
            trampoline(io, executor, runSyncFiber(k, (interpreter, next) -> {
                if (stackCounter.x < WORK_UNITS_PER_QUANTA)
                    trampoline(next, executor, interpreter, stackCounter);
                else
                    executor.schedule(() -> trampoline(next, executor, interpreter, stackCounter.reset()));
            }), stackCounter);
        }
    };
}