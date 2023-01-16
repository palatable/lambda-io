package com.jnape.palatable.lambda.effect.io.fiber.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;

import static com.jnape.palatable.lambda.functions.builtin.fn2.Filter.filter;
import static com.jnape.palatable.lambda.functions.builtin.fn3.FoldLeft.foldLeft;
import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.Arrays.copyOf;

public final class Benchmark {

    static final int OPS_PER_BENCHMARK = 1_000_000_000;

    private static final String ROOT_PACKAGE_PATH = join("/", "src", "test", "java");
    private static final String RESULTS_DIR_NAME  = "results";

    public static void runBenchmarks(Class<?> benchmarkClass, String... methods) throws RunnerException {
        new Runner(options(benchmarkClass, methods)).run();
    }

    private static Options options(Class<?> benchmarkClass, String[] methods) {
        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .resultFormat(ResultFormatType.JSON)
                .result(resultsFilePath(benchmarkClass));

        String benchmarkName = benchmarkClass.getCanonicalName();
        optionsBuilder = methods.length == 0
                         ? optionsBuilder.include(benchmarkName)
                         : foldLeft((b, m) -> b.include(benchmarkName + "." + m),
                                    optionsBuilder,
                                    asList(methods));

        return optionsBuilder.build();
    }

    private static String resultsFilePath(Class<?> benchmarkClass) {
        String   packageName          = benchmarkClass.getPackage().getName();
        String[] nestedClassesAndName = benchmarkClass.getCanonicalName().substring(packageName.length() + 1).split("\\.");
        String   packagePath          = joinPrune("/", packageName.split("\\."));
        String   nestedClassPath      = joinPrune("/", copyOf(nestedClassesAndName, nestedClassesAndName.length - 1));

        String resultsDirectoryPath = joinPrune("/", ROOT_PACKAGE_PATH, packagePath, RESULTS_DIR_NAME, nestedClassPath);
        String fileName             = joinPrune(".", nestedClassesAndName[nestedClassesAndName.length - 1], "jmh", "json");
        File   resultsDirectory     = new File(resultsDirectoryPath);
        if (!(resultsDirectory.mkdirs() || resultsDirectory.isDirectory())) {
            throw new IllegalStateException("Failed to create JMH results directory: " + resultsDirectoryPath);
        }
        return join("/", resultsDirectoryPath, fileName);
    }


    private static String joinPrune(CharSequence delimiter, CharSequence... parts) {
        return join(delimiter, filter(s -> !s.isEmpty(), asList(parts)));
    }
}