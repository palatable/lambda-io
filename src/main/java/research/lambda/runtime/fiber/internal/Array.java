package research.lambda.runtime.fiber.internal;

import com.jnape.palatable.lambda.adt.Maybe;
import com.jnape.palatable.shoki.api.Collection;
import com.jnape.palatable.shoki.api.Natural;
import com.jnape.palatable.shoki.api.Natural.NonZero;
import com.jnape.palatable.shoki.api.RandomAccess;
import com.jnape.palatable.shoki.api.SizeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import static com.jnape.palatable.lambda.adt.Maybe.just;
import static com.jnape.palatable.lambda.adt.Maybe.nothing;
import static com.jnape.palatable.shoki.api.Natural.atLeastZero;
import static com.jnape.palatable.shoki.api.Natural.zero;
import static com.jnape.palatable.shoki.api.SizeInfo.known;
import static java.util.Collections.emptyIterator;

public sealed interface Array<A> extends Collection<Natural, A>, RandomAccess<Natural, Maybe<A>> {

    @Override
    Array<A> tail();

    @Override
    Maybe<A> get(Natural natural);

    @Override
    boolean contains(Natural natural);

    @Override
    Maybe<A> head();

    @SuppressWarnings("unchecked")
    static <A> Array<A> empty() {
        return (Array<A>) EmptyArray.INSTANCE;
    }

    static <A> Array<A> withAppender(Consumer<? super Appender<? super A>> appenderCallback) {
        ArrayListBackedAppender<A> appender = new ArrayListBackedAppender<>(new ArrayList<>());
        appenderCallback.accept(appender);
        Object[] surrogate = appender.finish();
        return surrogate.length == 0 ? empty() : new NonEmptyReferenceArray<>(surrogate, 0);
    }

    sealed interface Appender<A> {
        void append(A a);

        void appendAll(Collection<?, A> collection);
    }

    static <A> Array<A> fill(NonZero length, A value) {
        Object[] array = new Object[length.intValue()];
        Arrays.fill(array, value);
        return new NonEmptyReferenceArray<>(array, 0);
    }

    static <A> Array<A> shallowCopy(A[] array, int offset) {
        return offset >= array.length
               ? empty()
               : new NonEmptyReferenceArray<A>(array.clone(), offset);
    }

    static Array<Byte> shallowCopy(byte[] array) {
        return shallowCopy(array, 0);
    }

    static Array<Short> shallowCopy(short[] array) {
        return shallowCopy(array, 0);
    }

    static Array<Integer> shallowCopy(int[] array) {
        return shallowCopy(array, 0);
    }

    static Array<Long> shallowCopy(long[] array) {
        return shallowCopy(array, 0);
    }

    static Array<Float> shallowCopy(float[] array) {
        return shallowCopy(array, 0);
    }

    static Array<Double> shallowCopy(double[] array) {
        return shallowCopy(array, 0);
    }

    static Array<Character> shallowCopy(char[] array) {
        return shallowCopy(array, 0);
    }

    static Array<Boolean> shallowCopy(boolean[] array) {
        return shallowCopy(array, 0);
    }

    static <A> Array<A> shallowCopy(A[] array) {
        return shallowCopy(array, 0);
    }

    static Array<Byte> shallowCopy(byte[] array, int offset) {
        return offset >= array.length
               ? empty()
               : new NonEmptyByteArray(array, offset);
    }

    static Array<Short> shallowCopy(short[] array, int offset) {
        return offset >= array.length
               ? empty()
               : new NonEmptyShortArray(array, offset);
    }

    static Array<Integer> shallowCopy(int[] array, int offset) {
        return offset >= array.length
               ? empty()
               : new NonEmptyIntegerArray(array, offset);
    }

    static Array<Long> shallowCopy(long[] array, int offset) {
        return offset >= array.length
               ? empty()
               : new NonEmptyLongArray(array, offset);
    }

    static Array<Float> shallowCopy(float[] array, int offset) {
        return offset >= array.length
               ? empty()
               : new NonEmptyFloatArray(array, offset);
    }

    static Array<Double> shallowCopy(double[] array, int offset) {
        return offset >= array.length
               ? empty()
               : new NonEmptyDoubleArray(array, offset);
    }

    static Array<Character> shallowCopy(char[] array, int offset) {
        return offset >= array.length
               ? empty()
               : new NonEmptyCharacterArray(array, offset);
    }

    static Array<Boolean> shallowCopy(boolean[] array, int offset) {
        return offset >= array.length
               ? empty()
               : new NonEmptyBooleanArray(array, offset);
    }
}

record EmptyArray<A>() implements Array<A> {
    static final EmptyArray<?> INSTANCE = new EmptyArray<>();

    @Override
    public EmptyArray<A> tail() {
        return this;
    }

    @Override
    public Maybe<A> get(Natural natural) {
        return nothing();
    }

    @Override
    public boolean contains(Natural natural) {
        return false;
    }

    @Override
    public Maybe<A> head() {
        return nothing();
    }

    @Override
    public SizeInfo.Known<Natural> sizeInfo() {
        return known(zero());
    }

    @Override
    public Iterator<A> iterator() {
        return emptyIterator();
    }
}

sealed interface NonEmptyArray<A> extends Array<A> {
    A unsafeGet(int x);

    int offset();

    int arrayLength();

    NonEmptyArray<A> unsafeTail();

    @Override
    default Array<A> tail() {
        return offset() + 1 == arrayLength() ? Array.empty() : unsafeTail();
    }

    @Override
    default Maybe<A> get(Natural natural) {
        int offsetIntValue = natural.intValue() + offset();
        return offsetIntValue < arrayLength() ? just(unsafeGet(offsetIntValue)) : nothing();
    }

    @Override
    default boolean contains(Natural natural) {
        return natural.intValue() + offset() < arrayLength();
    }

    @Override
    default Maybe<A> head() {
        return just(unsafeGet(offset()));
    }

    @Override
    default SizeInfo.Known<Natural> sizeInfo() {
        return known(atLeastZero(arrayLength() - offset()));
    }

    @Override
    default Iterator<A> iterator() {
        return new Iterator<>() {
            int i = offset();

            @Override
            public boolean hasNext() {
                return i < arrayLength();
            }

            @Override
            public A next() {
                if (!hasNext())
                    throw new NoSuchElementException();

                return unsafeGet(i++);
            }
        };
    }
}

record NonEmptyByteArray(byte[] arr, int offset) implements NonEmptyArray<Byte> {

    @Override
    public Byte unsafeGet(int x) {
        return arr[x];
    }

    @Override
    public int arrayLength() {
        return arr.length;
    }

    @Override
    public NonEmptyByteArray unsafeTail() {
        return new NonEmptyByteArray(arr, offset + 1);
    }

    @Override
    public String toString() {
        return "ByteArray" + Arrays.toString(arr);
    }
}

record NonEmptyShortArray(short[] arr, int offset) implements NonEmptyArray<Short> {

    @Override
    public Short unsafeGet(int x) {
        return arr[x];
    }

    @Override
    public int arrayLength() {
        return arr.length;
    }

    @Override
    public NonEmptyShortArray unsafeTail() {
        return new NonEmptyShortArray(arr, offset + 1);
    }

    @Override
    public String toString() {
        return "ShortArray" + Arrays.toString(arr);
    }
}

record NonEmptyIntegerArray(int[] arr, int offset) implements NonEmptyArray<Integer> {

    @Override
    public Integer unsafeGet(int x) {
        return arr[x];
    }

    @Override
    public int arrayLength() {
        return arr.length;
    }

    @Override
    public NonEmptyIntegerArray unsafeTail() {
        return new NonEmptyIntegerArray(arr, offset + 1);
    }

    @Override
    public String toString() {
        return "IntegerArray" + Arrays.toString(arr);
    }
}

record NonEmptyLongArray(long[] arr, int offset) implements NonEmptyArray<Long> {

    @Override
    public Long unsafeGet(int x) {
        return arr[x];
    }

    @Override
    public int arrayLength() {
        return arr.length;
    }

    @Override
    public NonEmptyLongArray unsafeTail() {
        return new NonEmptyLongArray(arr, offset + 1);
    }

    @Override
    public String toString() {
        return "LongArray" + Arrays.toString(arr);
    }
}

record NonEmptyFloatArray(float[] arr, int offset) implements NonEmptyArray<Float> {

    @Override
    public Float unsafeGet(int x) {
        return arr[x];
    }

    @Override
    public int arrayLength() {
        return arr.length;
    }

    @Override
    public NonEmptyFloatArray unsafeTail() {
        return new NonEmptyFloatArray(arr, offset + 1);
    }

    @Override
    public String toString() {
        return "FloatArray" + Arrays.toString(arr);
    }
}

record NonEmptyDoubleArray(double[] arr, int offset) implements NonEmptyArray<Double> {

    @Override
    public Double unsafeGet(int x) {
        return arr[x];
    }

    @Override
    public int arrayLength() {
        return arr.length;
    }

    @Override
    public NonEmptyDoubleArray unsafeTail() {
        return new NonEmptyDoubleArray(arr, offset + 1);
    }

    @Override
    public String toString() {
        return "DoubleArray" + Arrays.toString(arr);
    }
}

record NonEmptyCharacterArray(char[] arr, int offset) implements NonEmptyArray<Character> {

    @Override
    public Character unsafeGet(int x) {
        return arr[x];
    }

    @Override
    public int arrayLength() {
        return arr.length;
    }

    @Override
    public NonEmptyCharacterArray unsafeTail() {
        return new NonEmptyCharacterArray(arr, offset + 1);
    }

    @Override
    public String toString() {
        return "CharacterArray" + Arrays.toString(arr);
    }
}

record NonEmptyBooleanArray(boolean[] arr, int offset) implements NonEmptyArray<Boolean> {

    @Override
    public Boolean unsafeGet(int x) {
        return arr[x];
    }

    @Override
    public int arrayLength() {
        return arr.length;
    }

    @Override
    public NonEmptyBooleanArray unsafeTail() {
        return new NonEmptyBooleanArray(arr, offset + 1);
    }

    @Override
    public String toString() {
        return "BooleanArray" + Arrays.toString(arr);
    }
}

record NonEmptyReferenceArray<A>(Object[] arr, int offset) implements NonEmptyArray<A> {
    @Override
    @SuppressWarnings("unchecked")
    public A unsafeGet(int x) {
        return (A) arr[x];
    }

    @Override
    public int arrayLength() {
        return arr.length;
    }

    @Override
    public NonEmptyReferenceArray<A> unsafeTail() {
        return new NonEmptyReferenceArray<>(arr, offset + 1);
    }

    @Override
    public String toString() {
        return "ReferenceArray" + Arrays.toString(arr);
    }
}

final class ArrayListBackedAppender<A> implements Array.Appender<A> {
    private final    ArrayList<A> arrayList;
    private volatile boolean      finished = false;

    ArrayListBackedAppender(ArrayList<A> arrayList) {
        this.arrayList = arrayList;
    }

    public synchronized Object[] finish() {
        finished = true;
        return arrayList.toArray();
    }

    @Override
    public synchronized void append(A a) {
        ensureUnfinished();
        arrayList.add(a);
    }

    @Override
    public synchronized void appendAll(Collection<?, A> collection) {
        ensureUnfinished();
        collection.forEach(this::append);
    }

    private void ensureUnfinished() {
        if (finished)
            throw new IllegalStateException("Appender may only be interacted with" +
                                                    " inside Array#withAppender static factory method.");
    }
}