package com.jnape.palatable.lambda.effect.io.fiber;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CancellerTest {

    private Canceller canceller;

    @BeforeEach
    public void setUp() {
        canceller = Canceller.canceller();
    }

    @AfterEach
    public void tearDown() {
//        canceller.cancel();
//        canceller = null;
    }

    @Test
    public void isNotCancelledByDefault() {
        assertFalse(canceller.cancelled());
    }

    @Test
    public void isCancelledAfterCallingCancel() {
        canceller.cancel();
        assertTrue(canceller.cancelled());
    }

    @Test
    public void descendantsAreCancelledAndRemoved() {
        Canceller child1      = canceller.addChild();
        Canceller child2      = canceller.addChild();
        Canceller grandChild1 = child1.addChild();
        Canceller grandChild2 = child2.addChild();

        assertNotSame(canceller, child1);
        assertNotSame(canceller, child2);
        assertNotSame(child1, grandChild1);
        assertNotSame(child2, grandChild2);

        canceller.cancel();
        assertTrue(canceller.cancelled());
        assertTrue(child1.cancelled());
        assertTrue(child2.cancelled());
        assertTrue(grandChild1.cancelled());
        assertTrue(grandChild2.cancelled());
    }

    @Test
    public void childrenAreNotAddedWhenCancelled() {
        canceller.cancel();
        Canceller child = canceller.addChild();
        assertSame(canceller, child);
        assertTrue(child.cancelled());
    }

    @Test
    public void removeChildPreventsCancellationOfChild() {
        Canceller child1 = canceller.addChild();
        Canceller child2 = canceller.addChild();

        assertNotSame(canceller, child1);
        assertNotSame(canceller, child2);

        canceller.removeChild(child2);
        canceller.cancel();

        assertTrue(canceller.cancelled());
        assertTrue(child1.cancelled());
        assertFalse(child2.cancelled());

        child2.cancel();
        assertTrue(child2.cancelled());
    }

    @Test
    public void cancelCallbacksAreFiredInOrder() {
        List<String> interactions = new ArrayList<>();
        assertTrue(canceller.onCancellation(() -> interactions.add("first")));
        assertTrue(canceller.onCancellation(() -> interactions.add("second")));
        assertEquals(emptyList(), interactions);
        canceller.cancel();
        assertEquals(List.of("first", "second"), interactions);
    }

    @Test
    public void failuresInCancellationCallbacksDoNotInterfere() {
        List<String> interactions = new ArrayList<>();
        assertTrue(canceller.onCancellation(() -> {throw new IllegalStateException("kaboom");}));
        assertTrue(canceller.onCancellation(() -> interactions.add("second")));
        assertEquals(emptyList(), interactions);
        canceller.cancel();
        assertEquals(List.of("second"), interactions);
    }

    @Test
    public void callbacksAddedAfterCancellationAreNotRegistered() {
        List<String> interactions = new ArrayList<>();
        assertTrue(canceller.onCancellation(() -> interactions.add("first")));
        assertEquals(emptyList(), interactions);
        canceller.cancel();
        assertEquals(singletonList("first"), interactions);
        assertFalse(canceller.onCancellation(() -> interactions.add("second")));
        assertEquals(singletonList("first"), interactions);
    }
}