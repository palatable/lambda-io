package com.jnape.palatable.lambda.effect.io.fiber;

import static com.jnape.palatable.lambda.effect.io.fiber.Settings.loadBoolean;

//todo: enable settings
public record EnvironmentSettings(boolean interruptFuturesOnCancel/*,
                                  EventLoopThreading eventLoopThreading*/) {

    public static final EnvironmentSettings DEFAULT = new EnvironmentSettings(false);

    public static EnvironmentSettings system() {
        return System.LOADED;
    }

    private static final class System {
        private static final EnvironmentSettings LOADED = new EnvironmentSettings(
                loadBoolean(PropertyLabels.InterruptFuturesOnCancel.name())
                        .orElse(DEFAULT.interruptFuturesOnCancel));

        enum PropertyLabels {
            InterruptFuturesOnCancel
        }
    }
}