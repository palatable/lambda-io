package com.jnape.palatable.lambda.effect.io.fiber;

import com.jnape.palatable.lambda.adt.Maybe;
import com.jnape.palatable.lambda.functions.Fn1;
import com.jnape.palatable.lambda.functions.specialized.Kleisli;
import com.jnape.palatable.shoki.impl.HashMap;

import static com.jnape.palatable.lambda.adt.Maybe.maybe;
import static com.jnape.palatable.lambda.adt.Try.trying;
import static com.jnape.palatable.lambda.adt.hlist.HList.tuple;
import static com.jnape.palatable.lambda.monoid.builtin.First.first;
import static com.jnape.palatable.shoki.impl.HashMap.hashMap;
import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;

public final class Settings {

    private static final HashMap<String, Boolean> booleanMap = hashMap(tuple("true", true), tuple("false", false));

    private static final Kleisli<String, String, Maybe<?>, Maybe<String>> readProperty = s -> maybe(getProperty(s));
    private static final Kleisli<String, String, Maybe<?>, Maybe<String>> readEnv      = s -> maybe(getenv(s));

    private static final Kleisli<String, Boolean, Maybe<?>, Maybe<Boolean>> parseBool        = s -> booleanMap.get(s.toLowerCase());
    private static final Fn1<String, Maybe<Boolean>>                        readBoolProperty = readProperty.andThen(parseBool);
    private static final Fn1<String, Maybe<Boolean>>                        readBoolEnv      = readEnv.andThen(parseBool);
    private static final Kleisli<String, Integer, Maybe<?>, Maybe<Integer>> parseInt         = s -> trying(() -> parseInt(s)).toMaybe();
    private static final Fn1<String, Maybe<Integer>>                        readIntProperty  = readProperty.andThen(parseInt);
    private static final Fn1<String, Maybe<Integer>>                        readIntEnv       = readEnv.andThen(parseInt);

    private Settings() {
    }

    public static Maybe<Boolean> loadBoolean(String label) {
        return first(readBoolProperty.apply(label), readBoolEnv.apply(label));
    }

    public static Maybe<Integer> loadInteger(String label) {
        return first(readIntProperty.apply(label), readIntEnv.apply(label));
    }
}
