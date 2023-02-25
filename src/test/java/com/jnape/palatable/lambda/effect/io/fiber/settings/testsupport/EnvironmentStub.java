package com.jnape.palatable.lambda.effect.io.fiber.settings.testsupport;

import sun.misc.Unsafe;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jnape.palatable.lambda.adt.Try.trying;
import static com.jnape.palatable.lambda.functions.builtin.fn1.Constantly.constantly;
import static java.lang.System.getenv;

public final class EnvironmentStub {

    private EnvironmentStub() {
    }

    public static void withEnvironment(Map<String, String> env, Runnable scope) {
        Map<String, String> before = new HashMap<>(getenv());
        setEnv(env);
        trying(scope::run).ensuring(() -> setEnv(before)).orThrow();
    }

    // Credit: https://stackoverflow.com/a/7201825
    private static void setEnv(Map<String, String> newEnv) {
        ReflectionSupport.open(ReflectionSupport.getCurrentModule().getName(), "java.lang.reflect");
        ReflectionSupport.open(Field.class.getModule().getName(), "java.lang", "java.util");

        trying(() -> {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field    theEnvironmentField     = processEnvironmentClass.getDeclaredField("theEnvironment");

            ReflectionSupport.setAccessible(theEnvironmentField);
            @SuppressWarnings("unchecked")
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newEnv);
            @SuppressWarnings("JavaReflectionMemberAccess")
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass
                    .getDeclaredField("theCaseInsensitiveEnvironment");
            ReflectionSupport.setAccessible(theCaseInsensitiveEnvironmentField);
            @SuppressWarnings("unchecked")
            Map<String, String> ciEnv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
            ciEnv.putAll(newEnv);
        }).catchError(__ -> trying(() -> {
            Map<String, String> env = System.getenv();
            for (Class<?> clazz : Collections.class.getDeclaredClasses()) {
                if ("java.util.Collections$UnmodifiableMap".equals(clazz.getName())) {
                    Field field = clazz.getDeclaredField("m");
                    ReflectionSupport.setAccessible(field);
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = (Map<String, String>) field.get(env);
                    map.clear();
                    map.putAll(newEnv);
                }
            }
        })).orThrow();
    }

    // Credit: https://github.com/stefan-zobel/wip/blob/master/src/main/java/misc/AddOpens.java
    private static final class ReflectionSupport {

        private static final Unsafe U               = getUnsafe();
        private static final long   OVERRIDE_OFFSET = 12;

        private ReflectionSupport() {
        }

        public static void open(String moduleName, String... packageNames) {
            trying(() -> {
                Class<?> jlModule     = Class.forName("java.lang.Module");
                Module   thisModule   = getCurrentModule();
                Object   targetModule = findModule(moduleName);
                Method   m            = jlModule.getDeclaredMethod("implAddOpens", String.class, jlModule);
                setAccessible(m);
                for (String package_ : packageNames) {
                    m.invoke(targetModule, package_, thisModule);
                }
            });
        }

        private static Object findModule(String moduleName) {
            return trying(() -> {
                Class<?> moduleLayerClass = Class.forName("java.lang.ModuleLayer");
                Method   bootMethod       = moduleLayerClass.getDeclaredMethod("boot");
                Object   bootLayer        = bootMethod.invoke(null);
                Method   findModuleMethod = moduleLayerClass.getDeclaredMethod("findModule", String.class);
                return (Optional<?>) findModuleMethod.invoke(bootLayer, moduleName);
            }).recover(constantly(Optional.empty())).orElse(null);
        }

        private static Module getCurrentModule() {
            return trying(() -> {
                Method m = Class.class.getDeclaredMethod("getModule");
                setAccessible(m);
                return (Module) m.invoke(ReflectionSupport.class);
            }).recover(constantly(null));
        }

        private static void setAccessible(AccessibleObject accessibleObject) {
            U.putBoolean(accessibleObject, OVERRIDE_OFFSET, true);
        }

        private static Unsafe getUnsafe() {
            return trying(() -> {
                Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
                unsafe.setAccessible(true);
                return (Unsafe) unsafe.get(null);
            }).orThrow(constantly(new AssertionError("Unable to obtain an instance of sun.misc.Unsafe")));
        }
    }

}
