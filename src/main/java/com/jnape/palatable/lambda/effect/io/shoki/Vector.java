package com.jnape.palatable.lambda.effect.io.shoki;

import com.jnape.palatable.lambda.adt.Maybe;
import com.jnape.palatable.shoki.api.Collection;
import com.jnape.palatable.shoki.api.Natural;
import com.jnape.palatable.shoki.api.SizeInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Maybe.just;
import static com.jnape.palatable.shoki.api.Natural.atLeastZero;
import static com.jnape.palatable.shoki.api.SizeInfo.known;

public class Vector<A> implements Collection<Natural, A> {

    private static final Vector<?> EMPTY = new Vector<>(0, new Object[0]);

    private final int      index;
    private final Object[] table;

    private Vector(int index, Object[] table) {
        this.index = index;
        this.table = table;
    }

    @SuppressWarnings("unchecked")
    private static <A> Vector<A> empty() {
        return (Vector<A>) EMPTY;
    }

    @Override
    public SizeInfo.Known<Natural> sizeInfo() {
        return known(atLeastZero(table.length - index));
    }

    @Override
    public Vector<A> tail() {
        return index == table.length ? empty() : new Vector<>(index + 1, table);
    }

    @Override
    public Maybe<A> head() {
        @SuppressWarnings("unchecked")
        A a = (A) table[index];
        return just(a);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<A> iterator() {
        return index == table.length
               ? Collections.emptyIterator()
               : Spliterators.iterator((Spliterator<A>) Arrays.spliterator(table, index, table.length));
    }

    public static <A> Vector<A> vector(Consumer<Consumer<A>> withAppend) {
        return new Vector<>(0, new Object() {
            boolean built;
            int i = 0;
            Object[] arr = new Object[1024];

            {
                Consumer<A> append = a -> {
                    if (built)
                        throw new IllegalStateException("Vector is already built and may no longer be updated");
                    if (i == arr.length) arr = Arrays.copyOf(arr, arr.length * 2);
                    arr[i++] = a;
                };
                withAppend.accept(append);
                built = true;

                if (i < arr.length)
                    arr = Arrays.copyOf(arr, i);
            }

        }.arr);
    }

    public static <A> Vector<A> vector(int length, Consumer<BiConsumer<Integer, A>> withSet) {
        return new Vector<>(0, new Object() {
            boolean built;
            final Object[] arr = new Object[length];

            {
                BiConsumer<Integer, A> set = (i, a) -> {
                    if (built)
                        throw new IllegalStateException("Vector is already built and may no longer be updated");
                    arr[i] = a;
                };
                withSet.accept(set);
                built = true;
            }
        }.arr);
    }

    public static <A> Vector<A> wrap(Object[] arr) {
        return new Vector<>(0, arr);
    }

    public static <A> Vector<A> vector(java.util.Collection<A> collection) {
        return new Vector<>(0, collection.toArray());
    }
}
