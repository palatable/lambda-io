package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Maybe;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.shoki.impl.HashMap;

import static com.jnape.palatable.lambda.adt.Maybe.maybe;
import static com.jnape.palatable.lambda.adt.Try.trying;
import static com.jnape.palatable.lambda.adt.hlist.HList.tuple;
import static com.jnape.palatable.lambda.functions.Fn1.fn1;
import static com.jnape.palatable.shoki.impl.HashMap.hashMap;
import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;

public final class Settings {

    private static final HashMap<String, Boolean> booleanMap = hashMap(tuple("true", true), tuple("false", false));

    private Settings() {
    }

    public static Maybe<Boolean> loadBoolean(String label) {
        return loadAndParse(label, fn1(booleanMap::get).contraMap(String::toLowerCase));
    }

    public static Maybe<Integer> loadInteger(String label) {
        return loadAndParse(label, v -> trying(() -> parseInt(v)).toMaybe());
    }

    private static <A> Maybe<A> loadAndParse(String label, Fn1<? super String, ? extends Maybe<A>> tryParse) {
        return maybe(getProperty(label)).flatMap(tryParse).fmap(Maybe::just)
                .orElseGet(() -> maybe(getenv(label)).flatMap(tryParse));
    }
}
