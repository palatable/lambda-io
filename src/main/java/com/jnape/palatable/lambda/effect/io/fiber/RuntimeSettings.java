package com.jnape.palatable.lambda.effect.io.fiber;

import static com.jnape.palatable.lambda.effect.io.fiber.Settings.loadInteger;

//todo: enable additional settings
public record RuntimeSettings(int maxTicksBeforePreemption/*,
                              boolean debitBudgetForBindRightAssociation,
                              boolean treatThunksAsBlocking*/) {

    public static final RuntimeSettings DEFAULT = new RuntimeSettings(512/*, false, false*/);

    public static RuntimeSettings system() {
        return System.LOADED;
    }

    private static final class System {
        private static final RuntimeSettings LOADED = new RuntimeSettings(
                loadInteger(PropertyLabels.MaxTicksBeforePreemption.name())
                        .orElse(DEFAULT.maxTicksBeforePreemption)/*,
                loadBoolean(PropertyLabels.DebitBudgetForBindRightAssociation.name())
                        .orElse(DEFAULT.debitBudgetForBindRightAssociation),
                loadBoolean(PropertyLabels.DebitBudgetForBindRightAssociation.name())
                        .orElse(DEFAULT.debitBudgetForBindRightAssociation)*/);

        enum PropertyLabels {
            DebitBudgetForBindRightAssociation,
            TreatThunksAsBlocking,
            MaxTicksBeforePreemption
        }
    }
}
