import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public final class Benchmark {
    private static final int WARMUPS = 3;
    private static final int REPETITIONS = 9;
    private static final String[] OPERATIONS = {"insert", "search", "delete"};

    private static PrintWriter writer(Path path) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8));
    }

    private static void shuffle(int[] keys, Random random) {
        for (int i = keys.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temporary = keys[i];
            keys[i] = keys[j];
            keys[j] = temporary;
        }
    }

    private static int[][] workload(String scenario, int n, int trial) {
        Random random = new Random(42L + 1009L * n + trial);
        int step = 1;
        if (scenario.equals("collisions")) {
            step = 16;
            while (n > step * 3 / 4) {
                step *= 2;
            }
        }
        int[] insertion = new int[n];
        for (int i = 0; i < n; i++) {
            insertion[i] = i * step;
        }
        int[] search = insertion.clone();
        int[] deletion = insertion.clone();
        if (!scenario.equals("sorted")) {
            shuffle(insertion, random);
        }
        shuffle(search, random);
        shuffle(deletion, random);
        return new int[][] {insertion, search, deletion};
    }

    private static long[] measure(IntSet set, int[][] keys) {
        int n = keys[0].length;
        long[] times = new long[3];
        int successful = 0;
        long start = System.nanoTime();
        for (int key : keys[0]) {
            if (set.insert(key)) {
                successful++;
            }
        }
        times[0] = System.nanoTime() - start;
        require(successful == n && set.size() == n);
        successful = 0;
        start = System.nanoTime();
        for (int key : keys[1]) {
            if (set.contains(key)) {
                successful++;
            }
        }
        times[1] = System.nanoTime() - start;
        require(successful == n && set.size() == n);
        successful = 0;
        start = System.nanoTime();
        for (int key : keys[2]) {
            if (set.delete(key)) {
                successful++;
            }
        }
        times[2] = System.nanoTime() - start;
        require(successful == n && set.size() == 0);
        return times;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new AssertionError("Benchmark correctness check failed");
        }
    }

    private static long median(long[] values) {
        long[] copy = values.clone();
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    public static void main(String[] args) throws IOException {
        Path directory = Paths.get("results");
        Files.createDirectories(directory);
        try (PrintWriter environment = writer(directory.resolve("environment.txt"))) {
            environment.println("Recorded at: " + OffsetDateTime.now());
            environment.println("Java: " + System.getProperty("java.version"));
            environment.println("VM: " + System.getProperty("java.vm.name"));
            environment.println("JVM arguments: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
            environment.println("OS: " + System.getProperty("os.name") + " "
                    + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
            environment.println("CPU identifier: " + System.getenv("PROCESSOR_IDENTIFIER"));
            environment.println("Logical processors: " + Runtime.getRuntime().availableProcessors());
            environment.println("Maximum JVM heap bytes: " + Runtime.getRuntime().maxMemory());
            environment.println("Base seed: 42; workload seed: 42 + 1009 * n + trial");
            environment.println("Initial warmup: 2000 random 1000-element runs per structure");
            environment.println("Warmup trials per scenario and size: " + WARMUPS);
            environment.println("Measured trials per scenario and size: " + REPETITIONS);
            environment.println("Timer: System.nanoTime(); reported statistic: median batch time");
            environment.println("Execution is single-threaded; structure order alternates each trial.");
        }
        for (int i = 0; i < 2000; i++) {
            int[][] keys = workload("random", 1000, i);
            measure(new BinarySearchTree(), keys);
            measure(new HashTable(), keys);
        }
        try (PrintWriter raw = writer(directory.resolve("raw.csv"));
             PrintWriter summary = writer(directory.resolve("summary.csv"))) {
            raw.println("scenario,n,structure,trial,operation,total_ns");
            summary.println("scenario,n,structure,operation,median_ms,min_ms,max_ms,median_ns_per_operation");
            for (String scenario : new String[] {"random", "sorted", "collisions"}) {
                for (int n : new int[] {100, 1000, 10000}) {
                    long[][][] samples = new long[2][3][REPETITIONS];
                    for (int trial = -WARMUPS; trial < REPETITIONS; trial++) {
                        int[][] keys = workload(scenario, n, trial);
                        for (int order = 0; order < 2; order++) {
                            int structure = Math.floorMod(trial + order, 2);
                            IntSet set = structure == 0 ? new BinarySearchTree() : new HashTable();
                            long[] times = measure(set, keys);
                            if (trial >= 0) {
                                for (int operation = 0; operation < 3; operation++) {
                                    samples[structure][operation][trial] = times[operation];
                                    raw.printf(Locale.ROOT, "%s,%d,%s,%d,%s,%d%n", scenario,
                                            n, structure == 0 ? "BST" : "HashTable", trial + 1,
                                            OPERATIONS[operation], times[operation]);
                                }
                            }
                        }
                    }
                    for (int structure = 0; structure < 2; structure++) {
                        for (int operation = 0; operation < 3; operation++) {
                            long[] values = samples[structure][operation];
                            long med = median(values);
                            long min = Long.MAX_VALUE;
                            long max = Long.MIN_VALUE;
                            for (long value : values) {
                                min = Math.min(min, value);
                                max = Math.max(max, value);
                            }
                            summary.printf(Locale.ROOT, "%s,%d,%s,%s,%.6f,%.6f,%.6f,%.3f%n",
                                    scenario, n, structure == 0 ? "BST" : "HashTable",
                                    OPERATIONS[operation], med / 1000000.0, min / 1000000.0,
                                    max / 1000000.0, med / (double) n);
                        }
                    }
                    raw.flush();
                    summary.flush();
                    System.out.println("Completed " + scenario + ", n=" + n);
                }
            }
        }
        System.out.println("Saved results/raw.csv, results/summary.csv and results/environment.txt");
    }
}
