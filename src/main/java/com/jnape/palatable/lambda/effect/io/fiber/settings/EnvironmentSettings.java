package com.jnape.palatable.lambda.effect.io.fiber.settings;

import static com.jnape.palatable.lambda.effect.io.fiber.settings.Settings.loadBoolean;

public record EnvironmentSettings(boolean interruptFuturesOnCancel) {

    public static final EnvironmentSettings DEFAULT = new EnvironmentSettings(false);

    public static EnvironmentSettings system() {
        return System.LOADED;
    }

    static final class System {
        private static final EnvironmentSettings LOADED = load();

        static EnvironmentSettings load() {
            return new EnvironmentSettings(
                    loadBoolean(PropertyLabels.InterruptFuturesOnCancel.name())
                            .orElse(DEFAULT.interruptFuturesOnCancel));
        }

        enum PropertyLabels {
            InterruptFuturesOnCancel
        }
    }
}