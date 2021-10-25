package com.jnape.palatable.lambda.newIO;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.effect.io.Callback;
import com.jnape.palatable.lambda.functions.Fn0;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.SideEffect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;
import static com.jnape.palatable.lambda.newIO.UnsafeRunAsync.unsafeRunAsync;
import static com.jnape.palatable.lambda.newIO.UnsafeRunSync.unsafeRunSync;

@Deprecated
public abstract class IO<A> {

    private IO() {
    }

    public abstract <R> R interpret(Interpreter<A, R> interpreter);

    public final <B> IO<B> bind(Fn1<? super A, ? extends IO<B>> f) {
        return new Sequential<>(this, f);
    }

    public final <B> IO<B> ap(IO<Fn1<? super A, ? extends B>> ioF) {
        return new Parallel<>(this, ioF);
    }

    public static <A> IO<A> io(A a) {
        return new Value<>(a);
    }

    public static <A> IO<A> io(Fn0<A> thunk) {
        return new Suspension<>(thunk);
    }

    public static IO<Unit> io(SideEffect sideEffect) {
        return io(() -> {
            sideEffect.Ω();
            return UNIT;
        });
    }

    // - talk about interruptibility / cancellability

    public static <A> IO<A> async(Callback<? super Callback<? super A>> k) {
        return new Async<>(k);
    }

    public static <A> IO<A> async(Fn0<A> thunk, Executor executor) {
        return async(k -> executor.execute(() -> k.apply(thunk.apply())));
    }

    public final A unsafePerformIO() {
        return interpret(unsafeRunSync());
    }

    public final CompletableFuture<A> unsafePerformAsyncIO(Executor executor) {
        return interpret(unsafeRunAsync(executor));
    }

    public final CompletableFuture<A> unsafePerformAsyncIO() {
        return unsafePerformAsyncIO(ForkJoinPool.commonPool());
    }

    private static final class Value<A> extends IO<A> {
        private final A a;

        private Value(A a) {
            this.a = a;
        }

        @Override
        public <R> R interpret(Interpreter<A, R> interpreter) {
            return interpreter.interpret(a);
        }
    }

    private static final class Suspension<A> extends IO<A> {
        private final Fn0<A> thunk;

        private Suspension(Fn0<A> thunk) {
            this.thunk = thunk;
        }

        @Override
        public <R> R interpret(Interpreter<A, R> interpreter) {
            return interpreter.interpret(thunk);
        }
    }

    private static final class Async<A> extends IO<A> {
        private final Callback<? super Callback<? super A>> k;

        private Async(Callback<? super Callback<? super A>> k) {
            this.k = k;
        }

        @Override
        public <R> R interpret(Interpreter<A, R> interpreter) {
            return interpreter.interpret(k);
        }
    }

    private static final class Parallel<Z, A> extends IO<A> {
        private final IO<Z>                           ioZ;
        private final IO<Fn1<? super Z, ? extends A>> ioF;

        private Parallel(IO<Z> ioZ, IO<Fn1<? super Z, ? extends A>> ioF) {
            this.ioZ = ioZ;
            this.ioF = ioF;
        }

        @Override
        public <R> R interpret(Interpreter<A, R> interpreter) {
            return interpreter.interpret(ioZ, ioF);
        }
    }

    private static final class Sequential<Z, A> extends IO<A> {
        private final IO<Z>                           ioZ;
        private final Fn1<? super Z, ? extends IO<A>> f;

        private Sequential(IO<Z> ioZ, Fn1<? super Z, ? extends IO<A>> f) {
            this.ioZ = ioZ;
            this.f   = f;
        }

        @Override
        public <R> R interpret(Interpreter<A, R> interpreter) {
            return interpreter.interpret(ioZ, f);
        }
    }

    // 10 / us
    // 1 / 100 ns

    public static void main(String[] args) {
        int           n  = 3;
        ForkJoinPool  ex = ForkJoinPool.commonPool();
        AtomicInteger c  = new AtomicInteger();
//        times(n,
//              io -> io.ap(async(f -> ex.execute(() -> {
//                  int hy = c.incrementAndGet();
////                  if (hy % 10_000 == 0) {
//                      System.out.println(Thread.currentThread() + ": " + hy);
//                      Try.trying(() -> Thread.sleep(1000));
////                  }
//                  f.call(fn1(x -> x + 1));
//              }))),
//              async(() -> {
//                  start.set(System.currentTimeMillis());
//                  System.out.println("starting");
//                  return 0;
//              }, ex))
//                .unsafePerformAsyncIO()
//                .join();

//        System.out.print("buliding...");
//        IO<Integer> humongous = times(50_000_000,
//                                  io -> io.bind(x -> {
//                                      if (x % 1_000_000 == 0)
//                                          System.out.println(x);
//                                      return io(x + 1);
//                                  }),
//                                  io(() -> {
//                                      System.out.print("built. Running...");
//                                      return 0;
//                                  }));


        class Slot {
            long x = 0;
        }
        Slot slot = new Slot();

        long start = System.currentTimeMillis();
//        forever(io(() -> {
//            if (slot.x++ % 10_000_000 == 0) {
//                long now = System.currentTimeMillis();
//                long x   = slot.x - 1;
//                System.out.println(x + " (avg " + x / (now - start) + "/ms)");
//            }
//        })).unsafePerformIO();


        io(() -> {
            threadLog("foo");
            Thread.sleep(1000);
            return 42;
        }).ap(io(() -> {
            threadLog("bar");
            Thread.sleep(1000);
            return x -> x + 1;
        })).interpret(unsafeRunAsync(ex));

//        System.out.println(humongous.unsafePerformIO());
//        System.out.println((n / (System.currentTimeMillis() - start.get())) + "/ms");
    }

    public static <A, B> IO<B> forever(IO<A> io) {
        return io.bind(__ -> forever(io));
    }

    private static void threadLog(Object message) {
        System.out.println(Thread.currentThread() + ": " + message);
    }
}
