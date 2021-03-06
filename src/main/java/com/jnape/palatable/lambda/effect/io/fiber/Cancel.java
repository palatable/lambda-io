package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.shoki.impl.HashSet;
import com.jnape.palatable.shoki.impl.StrictQueue;

import java.util.concurrent.atomic.AtomicReference;

import static com.jnape.palatable.lambda.internal.Runtime.throwChecked;
import static com.jnape.palatable.shoki.impl.HashSet.hashSet;
import static com.jnape.palatable.shoki.impl.StrictQueue.strictQueue;

public final class Cancel {

    private static record CancelState(boolean cancelled, HashSet<Cancel> children, StrictQueue<Runnable> onCancel) {

        public CancelState cancel() {
            return cancelled ? this : new CancelState(true, hashSet(), strictQueue());
        }

        public CancelState removeChild(Cancel child) {
            return new CancelState(cancelled, children.remove(child), onCancel);
        }

        public CancelState addChild(Cancel child) {
            return cancelled ? this : new CancelState(cancelled, children.add(child), onCancel);
        }

        public CancelState onCancel(Runnable r) {
            return cancelled ? this : new CancelState(false, children, onCancel.snoc(r));
        }

        public static CancelState cancelState() {
            return new CancelState(false, hashSet(), strictQueue());
        }
    }

    private final AtomicReference<CancelState> stateRef = new AtomicReference<>(CancelState.cancelState());
    private final Cancel                       parent;

    private Cancel(Cancel parent) {
        this.parent = parent;
    }

    public boolean cancelled() {
        return stateRef.get().cancelled();
    }

    private void removeChild(Cancel child) {
        stateRef.updateAndGet(s -> s.removeChild(child));
    }

    public void onCancel(Runnable r) {
        if (stateRef.getAndUpdate(s -> s.onCancel(r)).cancelled())
            r.run();
    }

    public Cancel addChild() {
        Cancel child = new Cancel(this);
        return stateRef.getAndUpdate(s -> s.addChild(child)).cancelled() ? this : child;
    }

    public void cancel() {
        CancelState stored = stateRef.getAndUpdate(CancelState::cancel);
        if (!stored.cancelled()) {
            Exception e = null;
            try {
                stored.onCancel().forEach(Runnable::run);
            } catch (Exception t) {
                e = t;
            }

            try {
                stored.children().forEach(Cancel::cancel);
            } catch (Exception t2) {
                if (e == null) e = t2;
                else e.addSuppressed(t2);
            }

            if (parent != null)
                parent.removeChild(this);

            if (e != null) {
                throw throwChecked(e);
            }
        }
    }

    public static Cancel root() {
        return new Cancel(null);
    }
}
