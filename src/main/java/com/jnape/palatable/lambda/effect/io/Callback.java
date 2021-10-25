package com.jnape.palatable.lambda.effect.io;

import com.jnape.palatable.lambda.adt.Unit;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.internal.Runtime;

import static com.jnape.palatable.lambda.adt.Unit.UNIT;

public interface Callback<A> extends Fn1<A, Unit> {

    void checkedCall(A a) throws Exception;

    default void call(A a) {
        try {
            checkedCall(a);
        } catch (Exception e) {
            throw Runtime.throwChecked(e);
        }
    }

    @Override
    default Unit checkedApply(A a) throws Exception {
        checkedCall(a);
        return UNIT;
    }

    static <A> Callback<A> callback(Callback<? super A> callback) {
        return callback::apply;
    }
}
