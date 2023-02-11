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

//todo: Configuration interface? Either info for auto-building executors, or explicit executors?
public record Configuration(boolean treatThunksAsBlocking,
                            int maxTicksBeforePreemption) {

    public static final Configuration DEFAULT                    = new Configuration(false, 512);
    public static final Configuration UPGRADE_PATH_FROM_LAMBDA_5 = new Configuration(true,  512);

    private static volatile Configuration PROCESS;

    public static Configuration process() {
        if (PROCESS == null) {
            synchronized (Configuration.class) {
                if (PROCESS == null) {
                    PROCESS = load();
                }
            }
        }

        return PROCESS;
    }

    private static Configuration load() {
        HashMap<String, Boolean>                           bools        = hashMap(tuple("true", true), tuple("false", false));
        Kleisli<String, String, Maybe<?>, Maybe<String>>   readProperty = s -> maybe(getProperty(s));
        Kleisli<String, String, Maybe<?>, Maybe<String>>   readEnv      = s -> maybe(getenv(s));
        Kleisli<String, Boolean, Maybe<?>, Maybe<Boolean>> parseBool    = s -> bools.get(s.toLowerCase());
        Kleisli<String, Integer, Maybe<?>, Maybe<Integer>> parseInt     = s -> trying(() -> parseInt(s)).toMaybe();

        Fn1<String, Maybe<Boolean>> readBoolProperty = readProperty.andThen(parseBool);
        Fn1<String, Maybe<Boolean>> readBoolEnv      = readEnv.andThen(parseBool);
        Fn1<String, Maybe<Integer>> readIntProperty  = readProperty.andThen(parseInt);
        Fn1<String, Maybe<Integer>> readIntEnv       = readEnv.andThen(parseInt);

        Fn1<String, Maybe<Boolean>> readBool         = s -> first(readBoolProperty.apply(s), readBoolEnv.apply(s));
        Fn1<String, Maybe<Integer>> readInt          = s -> first(readIntProperty.apply(s), readIntEnv.apply(s));

        return new Configuration(
                readBool.apply(PropertyLabels.TreatThunksAsBlocking.name())
                        .orElse(DEFAULT.treatThunksAsBlocking),
                readInt.apply(PropertyLabels.MaxTicksBeforePreemption.name())
                        .orElse(DEFAULT.maxTicksBeforePreemption)
        );
    }

    enum PropertyLabels {
        MayInterruptFuturesOnCancel,
        TreatThunksAsBlocking,
        MaxTicksBeforePreemption
    }
}
