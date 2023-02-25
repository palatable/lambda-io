package com.jnape.palatable.lambda.effect.io.fiber.settings;

import org.junit.jupiter.api.Test;

import static com.jnape.palatable.lambda.effect.io.fiber.settings.EnvironmentSettings.DEFAULT;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.EnvironmentSettings.System.load;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.testsupport.EnvironmentStub.withEnvironment;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.testsupport.PropertiesStub.withProperties;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvironmentSettingsTest {

    @Test
    public void defaults() {
        withEnvironment(emptyMap(), () -> withProperties(emptyMap(), () ->
                assertEquals(DEFAULT, load())));
    }

    @Test
    public void loadsFromSystem() {
        withEnvironment(singletonMap("InterruptFuturesOnCancel", "true"),
                        () -> assertEquals(new EnvironmentSettings(true), load()));
        withProperties(singletonMap("InterruptFuturesOnCancel", "true"),
                       () -> assertEquals(new EnvironmentSettings(true), load()));
    }
}