package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.shoki.impl.HashSet;
import com.jnape.palatable.shoki.impl.StrictQueue;

import java.util.concurrent.atomic.AtomicReference;

import static com.jnape.palatable.lambda.effect.io.fiber.CancelState.fresh;
import static com.jnape.palatable.shoki.impl.HashSet.hashSet;
import static com.jnape.palatable.shoki.impl.StrictQueue.strictQueue;

public final class Canceller {

    private final Canceller                    parent;
    private final AtomicReference<CancelState> stateRef;

    private Canceller(Canceller parent, AtomicReference<CancelState> stateRef) {
        this.parent   = parent;
        this.stateRef = stateRef;
    }

    public boolean onCancellation(Runnable callback) {
        return !stateRef.updateAndGet(cs -> cs.addCallback(callback)).isCancelled();
    }

    public void cancel() {
        CancelState cancelState = stateRef.getAndSet(CancelState.cancelled());
        if (!cancelState.isCancelled()) {
            //todo: this will slow down cancel propagation -- consider relocating to shared FiberState or some such
            cancelState.callbacks().forEach(c -> {try {c.run();} catch (Exception ignored) {}});
            cancelState.children().forEach(Canceller::cancel);
            if (parent != null)
                parent.removeChild(this);
        }
    }

    public boolean cancelled() {
        return stateRef.get().isCancelled();
    }

    public Canceller addChild() {
        Canceller child = canceller(this);
        return stateRef
                       .updateAndGet(cs -> cs.addChild(child))
                       .isCancelled()
               ? this
               : child;
    }

    public void removeChild(Canceller child) {
        stateRef.updateAndGet(cs -> cs.removeChild(child));
    }

    public static Canceller canceller() {
        return canceller(null);
    }

    private static Canceller canceller(Canceller parent) {
        return new Canceller(parent, new AtomicReference<>(fresh()));
    }
}

record CancelState(boolean isCancelled, HashSet<Canceller> children, StrictQueue<Runnable> callbacks) {

    private static final CancelState CANCELLED = new CancelState(true, hashSet(), strictQueue());
    private static final CancelState FRESH     = new CancelState(false, hashSet(), strictQueue());

    CancelState addCallback(Runnable callback) {
        return isCancelled ? this : new CancelState(false, children, callbacks.snoc(callback));
    }

    CancelState addChild(Canceller child) {
        return isCancelled ? this : new CancelState(false, children.add(child), callbacks);
    }

    CancelState removeChild(Canceller child) {
        return new CancelState(isCancelled, children.remove(child), callbacks);
    }

    public static CancelState fresh() {
        return FRESH;
    }

    public static CancelState cancelled() {
        return CANCELLED;
    }
}