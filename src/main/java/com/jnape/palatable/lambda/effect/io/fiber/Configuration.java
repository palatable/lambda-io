package com.jnape.palatable.lambda.effect.io.fiber;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

//todo: Configuration interface? Either info for auto-building executors, or explicit executors?
public record Configuration(boolean mayInterruptFuturesOnCancel,
                            boolean treatThunksAsBlocking,
                            int maxTicksBeforePreemption) {

    public static final Configuration DEFAULT                    = new Configuration(false, false, 512);
    public static final Configuration UPGRADE_PATH_FROM_LAMBDA_5 = new Configuration(true, true, 512);

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
        Function<String, Optional<String>> readProperty = s -> Optional.ofNullable(System.getProperty(s));
        Function<String, Optional<String>> readEnv      = s -> Optional.ofNullable(System.getenv(s));

        Function<String, Optional<Boolean>> parseBool = s -> Optional.of(s.toLowerCase())
                .filter(Set.of("true", "false")::contains)
                .map(Boolean::parseBoolean);

        Function<String, Optional<Integer>> parseInt = s -> {
            try {
                return Optional.of(Integer.parseInt(s));
            } catch (NumberFormatException nfe) {
                return Optional.empty();
            }
        };

        Function<String, Optional<Boolean>> readBoolProperty = readProperty.andThen(opt -> opt.flatMap(parseBool));
        Function<String, Optional<Boolean>> readBoolEnv      = readEnv.andThen(opt -> opt.flatMap(parseBool));

        Function<String, Optional<Integer>> readIntProperty = readProperty.andThen(opt -> opt.flatMap(parseInt));
        Function<String, Optional<Integer>> readIntEnv      = readEnv.andThen(opt -> opt.flatMap(parseInt));

        Function<String, Optional<Boolean>> readBool = s -> readBoolProperty.apply(s)
                .or(() -> readBoolEnv.apply(s));
        Function<String, Optional<Integer>> readInt = s -> readIntProperty.apply(s)
                .or(() -> readIntEnv.apply(s));

        return new Configuration(
                readBool.apply(PropertyLabels.MayInterruptFuturesOnCancel.name())
                        .orElse(DEFAULT.mayInterruptFuturesOnCancel),
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
