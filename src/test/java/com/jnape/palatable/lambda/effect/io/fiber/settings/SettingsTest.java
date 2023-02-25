package com.jnape.palatable.lambda.effect.io.fiber.settings;

import org.junit.jupiter.api.Test;

import static com.jnape.palatable.lambda.adt.Maybe.just;
import static com.jnape.palatable.lambda.adt.Maybe.nothing;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.Settings.loadBoolean;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.Settings.loadInteger;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.testsupport.EnvironmentStub.withEnvironment;
import static com.jnape.palatable.lambda.effect.io.fiber.settings.testsupport.PropertiesStub.withProperties;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SettingsTest {

    @Test
    public void missing() {
        assertEquals(nothing(), loadBoolean("missing"));
    }

    @Test
    public void notParseable() {
        withProperties(singletonMap("invalid", "-1"), () -> assertEquals(nothing(), loadBoolean("invalid")));
        withProperties(singletonMap("invalid", "foo"), () -> assertEquals(nothing(), loadInteger("invalid")));

        withEnvironment(singletonMap("invalid", "-1"), () -> assertEquals(nothing(), loadBoolean("invalid")));
        withEnvironment(singletonMap("invalid", "foo"), () -> assertEquals(nothing(), loadInteger("invalid")));
    }

    @Test
    public void propertyParseFailureSupersedesEnvironmentParseSuccess() {
        withProperties(singletonMap("sketchy", "-1"), () -> withEnvironment(singletonMap("sketchy", "true"), () ->
                assertEquals(nothing(), loadBoolean("sketchy"))));
    }

    @Test
    public void propertyParseSuccessSupersedesEnvironmentParseFailure() {
        withProperties(singletonMap("sketchy", "true"), () -> withEnvironment(singletonMap("sketchy", "-1"), () ->
                assertEquals(just(true), loadBoolean("sketchy"))));
    }

    @Test
    public void propertyOnly() {
        withProperties(singletonMap("property", "true"), () -> assertEquals(just(true), loadBoolean("property")));
        withProperties(singletonMap("property", "1"), () -> assertEquals(just(1), loadInteger("property")));
    }

    @Test
    public void environmentOnly() {
        withEnvironment(singletonMap("variable", "true"), () -> assertEquals(just(true), loadBoolean("variable")));
        withEnvironment(singletonMap("variable", "1"), () -> assertEquals(just(1), loadInteger("variable")));
    }

    @Test
    public void propertiesHavePrecedence() {
        withProperties(singletonMap("mixed", "true"), () -> withEnvironment(singletonMap("mixed", "false"), () ->
                assertEquals(just(true), loadBoolean("mixed"))));
    }

    @Test
    public void booleansAreCaseInsensitive() {
        withEnvironment(singletonMap("variable", "tRuE"), () -> assertEquals(just(true), loadBoolean("variable")));
        withEnvironment(singletonMap("variable", "FaLsE"), () -> assertEquals(just(false), loadBoolean("variable")));

        withProperties(singletonMap("property", "tRuE"), () -> assertEquals(just(true), loadBoolean("property")));
        withProperties(singletonMap("property", "FaLsE"), () -> assertEquals(just(false), loadBoolean("property")));
    }
}