package com.jnape.palatable.lambda.effect.io.fiber.settings.testsupport;

import java.util.Map;
import java.util.Properties;

import static com.jnape.palatable.lambda.adt.Try.trying;
import static java.lang.System.getProperties;
import static java.lang.System.setProperties;

public final class PropertiesStub {

    private PropertiesStub() {
    }

    public static void withProperties(Map<String, String> properties, Runnable scope) {
        Properties previous = getProperties();
        setProperties(new Properties() {{
            putAll(properties);
        }});
        trying(scope::run).ensuring(() -> setProperties(previous)).orThrow();
    }
}
