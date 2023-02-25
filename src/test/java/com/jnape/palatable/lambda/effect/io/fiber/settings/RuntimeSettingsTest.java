package com.jnape.palatable.lambda.effect.io.fiber.settings;

import org.junit.jupiter.api.Test;

import static com.jnape.palatable.lambda.effect.io.fiber.settings.RuntimeSettings.DEFAULT;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.RuntimeSettings.System.load;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.testsupport.EnvironmentStub.withEnvironment;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.testsupport.PropertiesStub.withProperties;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RuntimeSettingsTest {

    @Test
    public void defaults() {
        withEnvironment(emptyMap(), () -> withProperties(emptyMap(), () ->
                assertEquals(DEFAULT, load())));
    }

    @Test
    public void loadsFromSystem() {
        withEnvironment(singletonMap("MaxTicksBeforePreemption", "10"),
                        () -> assertEquals(new RuntimeSettings(10), load()));
        withProperties(singletonMap("MaxTicksBeforePreemption", "10"),
                       () -> assertEquals(new RuntimeSettings(10), load()));
    }

    @Test
    public void nonPositiveMaxTicksIsInvalid() {
        withEnvironment(singletonMap("MaxTicksBeforePreemption", "0"),
                        () -> assertEquals(DEFAULT, load()));
        withProperties(singletonMap("MaxTicksBeforePreemption", "0"),
                       () -> assertEquals(DEFAULT, load()));
    }
}