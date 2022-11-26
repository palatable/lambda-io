package com.jnape.palatable.lambda.runtime.fiber;

import com.jnape.palatable.shoki.impl.HashSet;

import java.util.concurrent.atomic.AtomicReference;

import static com.jnape.palatable.lambda.runtime.fiber.CancelState.fresh;
import static com.jnape.palatable.shoki.impl.HashSet.hashSet;

public sealed interface Canceller permits ThreadSafeCancelTree {

    void cancel();

    boolean cancelled();

    Canceller addChild();

    void removeChild(Canceller child);

    static Canceller canceller() {
        return new ThreadSafeCancelTree(null);
    }
}

final class ThreadSafeCancelTree implements Canceller {

    private final Canceller                    parent;
    private final AtomicReference<CancelState> stateRef = new AtomicReference<>(fresh());

    ThreadSafeCancelTree(Canceller parent) {
        this.parent = parent;
    }

    @Override
    public void cancel() {
        CancelState cancelState = stateRef.getAndSet(CancelState.cancelled());
        if (!cancelState.isCancelled()) {
            cancelState.children().forEach(Canceller::cancel);
            if (parent != null)
                parent.removeChild(this);
        }
    }

    @Override
    public boolean cancelled() {
        return stateRef.get().isCancelled();
    }

    @Override
    public Canceller addChild() {
        ThreadSafeCancelTree child = new ThreadSafeCancelTree(this);
        return stateRef
                       .updateAndGet(cs -> cs.addChild(child))
                       .isCancelled()
               ? this
               : child;
    }

    @Override
    public void removeChild(Canceller child) {
        stateRef.updateAndGet(cs -> cs.removeChild(child));
    }
}

record CancelState(boolean isCancelled, HashSet<Canceller> children) {

    private static final CancelState CANCELLED = new CancelState(true, hashSet());
    private static final CancelState FRESH     = new CancelState(false, hashSet());

    CancelState addChild(Canceller child) {
        return isCancelled ? this : new CancelState(false, children.add(child));
    }

    CancelState removeChild(Canceller child) {
        return new CancelState(isCancelled, children.remove(child));
    }

    public static CancelState fresh() {
        return FRESH;
    }

    public static CancelState cancelled() {
        return CANCELLED;
    }
}