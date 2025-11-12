import java.util.*;

/**
 * Reusable utility for calculating and printing task execution statistics.
 * Used by all instrumented implementations (ThreadPool, ForkJoin tiles, ForkJoin recursive).
 *
 * Provides common statistical analysis and visualization methods to avoid code duplication
 * across different implementations.
 */
public class TaskStatistics {

    /**
     * Generic task timing interface that implementations must provide.
     */
    public interface TaskTiming {
        double getDurationMs();
        String getThreadName();
    }

    /**
     * Interface for tasks that have spatial position (for heatmap visualization).
     */
    public interface SpatialTask extends TaskTiming {
        int getStartX();
        int getStartY();
    }

    /**
     * Calculate and print basic statistics (min, max, avg, median, std dev, percentiles).
     */
    public static void printBasicStatistics(List<? extends TaskTiming> timings) {
        double[] times = timings.stream()
            .mapToDouble(TaskTiming::getDurationMs)
            .sorted()
            .toArray();

        if (times.length == 0) {
            System.out.println("No tasks to analyze.");
            return;
        }

        double minTime = times[0];
        double maxTime = times[times.length - 1];
        double avgTime = Arrays.stream(times).average().orElse(0);
        double medianTime = times[times.length / 2];

        // Calculate standard deviation
        double variance = Arrays.stream(times)
            .map(t -> Math.pow(t - avgTime, 2))
            .average()
            .orElse(0);
        double stdDev = Math.sqrt(variance);

        // Percentiles
        double p95 = times[(int)(times.length * 0.95)];
        double p99 = times[(int)(times.length * 0.99)];

        System.out.printf("Total tasks:     %d%n", timings.size());
        System.out.printf("Min time:        %.3f ms%n", minTime);
        System.out.printf("Max time:        %.3f ms%n", maxTime);
        System.out.printf("Average time:    %.3f ms%n", avgTime);
        System.out.printf("Median time:     %.3f ms%n", medianTime);
        System.out.printf("Std deviation:   %.3f ms%n", stdDev);
        System.out.printf("95th percentile: %.3f ms%n", p95);
        System.out.printf("99th percentile: %.3f ms%n", p99);
        System.out.printf("Variability:     %.1fx (max/min ratio)%n", maxTime / minTime);
        System.out.printf("Coefficient of variation: %.1f%%%n", (stdDev / avgTime) * 100);
    }

    /**
     * Print execution time distribution histogram.
     */
    public static void printDistribution(List<? extends TaskTiming> timings) {
        double[] times = timings.stream()
            .mapToDouble(TaskTiming::getDurationMs)
            .sorted()
            .toArray();

        if (times.length == 0) return;

        double minTime = times[0];
        double maxTime = times[times.length - 1];

        System.out.println();
        System.out.println("EXECUTION TIME DISTRIBUTION:");

        int numBins = 20;
        double binWidth = (maxTime - minTime) / numBins;
        int[] bins = new int[numBins];

        for (double time : times) {
            int binIndex = Math.min((int)((time - minTime) / binWidth), numBins - 1);
            bins[binIndex]++;
        }

        int maxBinCount = Arrays.stream(bins).max().orElse(1);
        int barWidth = 40;

        for (int i = 0; i < numBins; i++) {
            double rangeStart = minTime + i * binWidth;
            double rangeEnd = minTime + (i + 1) * binWidth;
            int barLen = (int)((bins[i] / (double)maxBinCount) * barWidth);

            System.out.printf("%7.1f-%5.1fms [%4d]: %s%n",
                             rangeStart, rangeEnd, bins[i],
                             "█".repeat(barLen));
        }
    }

    /**
     * Print per-thread statistics.
     */
    public static void printPerThreadStatistics(List<? extends TaskTiming> timings) {
        System.out.println();
        System.out.println("PER-THREAD STATISTICS:");

        Map<String, List<TaskTiming>> timingsByThread = new TreeMap<>();
        for (TaskTiming timing : timings) {
            timingsByThread.computeIfAbsent(timing.getThreadName(), k -> new ArrayList<>()).add(timing);
        }

        for (Map.Entry<String, List<TaskTiming>> entry : timingsByThread.entrySet()) {
            String threadName = entry.getKey();
            List<TaskTiming> threadTimings = entry.getValue();

            double avgTime = threadTimings.stream()
                .mapToDouble(TaskTiming::getDurationMs)
                .average()
                .orElse(0);

            double totalTime = threadTimings.stream()
                .mapToDouble(TaskTiming::getDurationMs)
                .sum();

            System.out.printf("  %s: %d tasks, avg %.3f ms, total %.3f ms%n",
                             threadName, threadTimings.size(), avgTime, totalTime);
        }
    }

    /**
     * Print load balance analysis based on thread total times.
     */
    public static void printLoadBalanceAnalysis(List<? extends TaskTiming> timings) {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("LOAD BALANCE ANALYSIS");
        System.out.println("======================================================================");

        // Group timings by thread
        Map<String, List<TaskTiming>> timingsByThread = new TreeMap<>();
        for (TaskTiming timing : timings) {
            timingsByThread.computeIfAbsent(timing.getThreadName(), k -> new ArrayList<>()).add(timing);
        }

        // Calculate per-thread total times
        double[] threadTotals = timingsByThread.values().stream()
            .mapToDouble(list -> list.stream()
                .mapToDouble(TaskTiming::getDurationMs)
                .sum())
            .toArray();

        if (threadTotals.length == 0) {
            System.out.println("No thread data to analyze.");
            return;
        }

        double minThreadTime = Arrays.stream(threadTotals).min().orElse(0);
        double maxThreadTime = Arrays.stream(threadTotals).max().orElse(0);
        double avgThreadTime = Arrays.stream(threadTotals).average().orElse(0);

        System.out.printf("Thread total times: min=%.3f ms, max=%.3f ms, avg=%.3f ms%n",
                         minThreadTime, maxThreadTime, avgThreadTime);

        double imbalance = maxThreadTime - minThreadTime;
        double efficiency = (minThreadTime / maxThreadTime) * 100;

        System.out.printf("Time imbalance: %.3f ms (%.1f%% of max)%n",
                         imbalance, (imbalance / maxThreadTime) * 100);
        System.out.printf("Load balance efficiency: %.1f%%%n", efficiency);

        if (efficiency < 70) {
            System.out.println();
            System.out.println("⚠ Poor load balancing detected!");
            System.out.println("  Work-stealing couldn't fully compensate for task variability.");
            System.out.println("  Consider adjusting task granularity.");
        } else if (efficiency > 95) {
            System.out.println();
            System.out.println("✓ Excellent load balancing!");
        } else {
            System.out.println();
            System.out.println("✓ Good load balance! Work-stealing effectively distributed work.");
        }
    }

    /**
     * Print slowest and fastest tasks.
     */
    public static <T extends TaskTiming> void printExtremes(
            List<T> timings,
            int count,
            TaskFormatter<T> formatter) {

        System.out.println();
        System.out.println("SLOWEST " + count + " TASKS:");
        timings.stream()
            .sorted((a, b) -> Double.compare(b.getDurationMs(), a.getDurationMs()))
            .limit(count)
            .forEach(t -> System.out.printf("  %s: %.3f ms (thread: %s)%n",
                                           formatter.format(t), t.getDurationMs(), t.getThreadName()));

        System.out.println();
        System.out.println("FASTEST " + count + " TASKS:");
        timings.stream()
            .sorted((a, b) -> Double.compare(a.getDurationMs(), b.getDurationMs()))
            .limit(count)
            .forEach(t -> System.out.printf("  %s: %.3f ms (thread: %s)%n",
                                           formatter.format(t), t.getDurationMs(), t.getThreadName()));
    }

    /**
     * Functional interface for formatting task-specific information.
     */
    @FunctionalInterface
    public interface TaskFormatter<T> {
        String format(T task);
    }

    /**
     * Print spatial heatmap showing execution time distribution across 2D space.
     * Requires tasks to implement SpatialTask interface.
     */
    public static <T extends SpatialTask> void printSpatialHeatmap(
            List<T> timings,
            int imageWidth,
            int imageHeight,
            int tileSize) {

        System.out.println();
        System.out.println("SPATIAL DISTRIBUTION OF EXECUTION TIMES:");
        System.out.println("  (Heatmap: darker = longer execution time)");
        System.out.println();

        // Build map of execution times by position
        Map<String, Double> timeByPosition = new HashMap<>();
        double maxTime = 0;
        double minTime = Double.MAX_VALUE;

        for (T task : timings) {
            String key = task.getStartX() + "," + task.getStartY();
            double duration = task.getDurationMs();
            timeByPosition.put(key, duration);
            maxTime = Math.max(maxTime, duration);
            minTime = Math.min(minTime, duration);
        }

        // Print heatmap
        String[] intensityChars = {" ", "░", "▒", "▓", "█"};

        for (int y = 0; y < imageHeight; y += tileSize) {
            System.out.print("  ");
            for (int x = 0; x < imageWidth; x += tileSize) {
                String key = x + "," + y;
                Double time = timeByPosition.get(key);

                if (time != null) {
                    double normalized = (time - minTime) / (maxTime - minTime + 0.001);
                    int intensity = (int)(normalized * (intensityChars.length - 1));
                    System.out.print(intensityChars[intensity]);
                } else {
                    System.out.print(" ");
                }
            }
            System.out.println();
        }

        System.out.printf("  Legend: '%s' = %.2fms, '%s' = %.2fms%n",
                         intensityChars[0], minTime,
                         intensityChars[intensityChars.length - 1], maxTime);
    }

    /**
     * Print full statistics report (basic stats, distribution, per-thread, load balance).
     */
    public static void printFullReport(List<? extends TaskTiming> timings) {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("TASK EXECUTION TIME STATISTICS");
        System.out.println("======================================================================");

        printBasicStatistics(timings);
        printDistribution(timings);
        printPerThreadStatistics(timings);
        printLoadBalanceAnalysis(timings);
    }

    /**
     * Print full report with spatial heatmap (for tile-based implementations).
     */
    public static <T extends SpatialTask> void printFullReportWithHeatmap(
            List<T> timings,
            int imageWidth,
            int imageHeight,
            int tileSize) {

        printFullReport(timings);
        printSpatialHeatmap(timings, imageWidth, imageHeight, tileSize);
    }
}
