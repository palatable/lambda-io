package com.jnape.palatable.lambda.effect.io.fiber.settings;

import static com.jnape.palatable.lambda.effect.io.fiber.settings.Settings.loadInteger;
import static com.jnape.palatable.lambda.functions.builtin.fn2.GT.gt;

//todo: enable additional settings
public record RuntimeSettings(int maxTicksBeforePreemption/*,
                              boolean debitBudgetForBindRightAssociation,
                              boolean treatThunksAsBlocking*/) {

    public static final RuntimeSettings DEFAULT = new RuntimeSettings(512/*, false, false*/);

    public static RuntimeSettings system() {
        return System.LOADED;
    }

    static final class System {
        private static final RuntimeSettings LOADED = load();

        static RuntimeSettings load() {
            return new RuntimeSettings(
                    loadInteger(PropertyLabels.MaxTicksBeforePreemption.name())
                            .filter(gt(0))
                            .orElse(DEFAULT.maxTicksBeforePreemption)/*,
                loadBoolean(PropertyLabels.DebitBudgetForBindRightAssociation.name())
                        .orElse(DEFAULT.debitBudgetForBindRightAssociation),
                loadBoolean(PropertyLabels.DebitBudgetForBindRightAssociation.name())
                        .orElse(DEFAULT.debitBudgetForBindRightAssociation)*/);
        }

        enum PropertyLabels {
            DebitBudgetForBindRightAssociation,
            TreatThunksAsBlocking,
            MaxTicksBeforePreemption
        }
    }
}
