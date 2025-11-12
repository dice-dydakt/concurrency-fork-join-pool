import java.util.*;

/**
 * Simplified execution tracker that hides implementation details.
 * Implementations just call record() and printReport() 
 *
 * Usage:
 *   ExecutionTracker tracker = new ExecutionTracker(true);  // enabled
 *   ExecutionTracker tracker = new ExecutionTracker(false); // disabled (no-op)
 *
 *   // In task:
 *   long start = System.nanoTime();
 *   doWork();
 *   long end = System.nanoTime();
 *   tracker.record(start, end, Thread.currentThread().getName());
 *
 *   // At end:
 *   tracker.printReport();
 */
public class ExecutionTracker {

    private final List<TaskRecord> records;
    private final boolean enabled;

    /**
     * Internal record of task execution.
     */
    private static class TaskRecord {
        final long startNanos;
        final long endNanos;
        final String threadName;
        final Object metadata;  // Optional: for tile position, region info, etc.

        TaskRecord(long startNanos, long endNanos, String threadName, Object metadata) {
            this.startNanos = startNanos;
            this.endNanos = endNanos;
            this.threadName = threadName;
            this.metadata = metadata;
        }

        double getDurationMs() {
            return (endNanos - startNanos) / 1_000_000.0;
        }
    }

    /**
     * Create an enabled tracker (collects detailed statistics).
     */
    public ExecutionTracker(boolean enabled) {
        this.enabled = enabled;
        this.records = enabled ? Collections.synchronizedList(new ArrayList<>()) : null;
    }

    /**
     * Create a tracker (enabled by default for backward compatibility).
     */
    public ExecutionTracker() {
        this(true);
    }

    /**
     * Check if tracking is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Record a task execution (no-op if disabled).
     */
    public void record(long startNanos, long endNanos, String threadName) {
        if (enabled) {
            records.add(new TaskRecord(startNanos, endNanos, threadName, null));
        }
    }

    /**
     * Record a task execution with metadata (no-op if disabled).
     */
    public void record(long startNanos, long endNanos, String threadName, Object metadata) {
        if (enabled) {
            records.add(new TaskRecord(startNanos, endNanos, threadName, metadata));
        }
    }

    /**
     * Get number of tasks recorded.
     */
    public int getTaskCount() {
        return records.size();
    }

    /**
     * Print complete statistics report (no-op if disabled).
     */
    public void printReport() {
        if (!enabled) {
            return;  // No-op if tracking disabled
        }

        System.out.println();
        System.out.println("======================================================================");
        System.out.println("TASK EXECUTION TIME STATISTICS");
        System.out.println("======================================================================");

        printBasicStatistics();
        printDistribution();
        printPerThreadStatistics();
        printLoadBalanceAnalysis();
    }

    /**
     * Print basic statistics.
     */
    private void printBasicStatistics() {
        if (records.isEmpty()) {
            System.out.println("No tasks recorded.");
            return;
        }

        double[] times = records.stream()
            .mapToDouble(TaskRecord::getDurationMs)
            .sorted()
            .toArray();

        double minTime = times[0];
        double maxTime = times[times.length - 1];
        double avgTime = Arrays.stream(times).average().orElse(0);
        double medianTime = times[times.length / 2];

        // Standard deviation
        double variance = Arrays.stream(times)
            .map(t -> Math.pow(t - avgTime, 2))
            .average()
            .orElse(0);
        double stdDev = Math.sqrt(variance);

        // Percentiles
        double p95 = times[(int)(times.length * 0.95)];
        double p99 = times[(int)(times.length * 0.99)];

        System.out.printf("Total tasks:     %d%n", records.size());
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
    private void printDistribution() {
        if (records.isEmpty()) return;

        double[] times = records.stream()
            .mapToDouble(TaskRecord::getDurationMs)
            .sorted()
            .toArray();

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
    private void printPerThreadStatistics() {
        if (records.isEmpty()) return;

        System.out.println();
        System.out.println("PER-THREAD STATISTICS:");

        Map<String, List<TaskRecord>> recordsByThread = new TreeMap<>();
        for (TaskRecord record : records) {
            recordsByThread.computeIfAbsent(record.threadName, k -> new ArrayList<>()).add(record);
        }

        for (Map.Entry<String, List<TaskRecord>> entry : recordsByThread.entrySet()) {
            String threadName = entry.getKey();
            List<TaskRecord> threadRecords = entry.getValue();

            double avgTime = threadRecords.stream()
                .mapToDouble(TaskRecord::getDurationMs)
                .average()
                .orElse(0);

            double totalTime = threadRecords.stream()
                .mapToDouble(TaskRecord::getDurationMs)
                .sum();

            System.out.printf("  %s: %d tasks, avg %.3f ms, total %.3f ms%n",
                             threadName, threadRecords.size(), avgTime, totalTime);
        }
    }

    /**
     * Print load balance analysis.
     */
    private void printLoadBalanceAnalysis() {
        if (records.isEmpty()) return;

        System.out.println();
        System.out.println("======================================================================");
        System.out.println("LOAD BALANCE ANALYSIS");
        System.out.println("======================================================================");

        Map<String, List<TaskRecord>> recordsByThread = new TreeMap<>();
        for (TaskRecord record : records) {
            recordsByThread.computeIfAbsent(record.threadName, k -> new ArrayList<>()).add(record);
        }

        double[] threadTotals = recordsByThread.values().stream()
            .mapToDouble(list -> list.stream()
                .mapToDouble(TaskRecord::getDurationMs)
                .sum())
            .toArray();

        if (threadTotals.length == 0) {
            System.out.println("No thread data to analyze.");
            return;
        }

        double minThreadTime = Arrays.stream(threadTotals).min().orElse(0);
        double maxThreadTime = Arrays.stream(threadTotals).max().orElse(0);
        double avgThreadTime = Arrays.stream(threadTotals).average().orElse(0);

        System.out.println("Total work per thread (sum of all task durations):");
        System.out.printf("  Min: %.3f ms (least loaded thread)%n", minThreadTime);
        System.out.printf("  Max: %.3f ms (most loaded thread)%n", maxThreadTime);
        System.out.printf("  Avg: %.3f ms%n", avgThreadTime);

        double imbalance = maxThreadTime - minThreadTime;
        double efficiency = (minThreadTime / maxThreadTime) * 100;

        System.out.printf("Time imbalance: %.3f ms (%.1f%% of max)%n",
                         imbalance, (imbalance / maxThreadTime) * 100);
        System.out.printf("Load balance efficiency: %.1f%%%n", efficiency);

        if (efficiency < 70) {
            System.out.println();
            System.out.println("⚠ Poor load balancing detected!");
            System.out.println("  Consider adjusting task granularity for better work distribution.");
        } else if (efficiency > 95) {
            System.out.println();
            System.out.println("✓ Excellent load balancing!");
        } else {
            System.out.println();
            System.out.println("✓ Good load balance!");
        }
    }

    /**
     * Print spatial heatmap (for tile-based implementations).
     * Metadata must contain position information (no-op if disabled).
     */
    public void printSpatialHeatmap(int imageWidth, int imageHeight, int tileSize,
                                    PositionExtractor extractor) {
        if (!enabled || records.isEmpty()) return;

        System.out.println();
        System.out.println("SPATIAL DISTRIBUTION OF EXECUTION TIMES:");
        System.out.println("  (Heatmap: lighter = longer execution time)");
        System.out.println();

        Map<String, Double> timeByPosition = new HashMap<>();
        double maxTime = 0;
        double minTime = Double.MAX_VALUE;

        for (TaskRecord record : records) {
            if (record.metadata != null) {
                int[] pos = extractor.getPosition(record.metadata);
                String key = pos[0] + "," + pos[1];
                double duration = record.getDurationMs();
                timeByPosition.put(key, duration);
                maxTime = Math.max(maxTime, duration);
                minTime = Math.min(minTime, duration);
            }
        }

        // Characters from light to dark: darker = longer time
        String[] intensityChars = {" ", "░", "▒", "▓", "█"};

        for (int y = 0; y < imageHeight; y += tileSize) {
            System.out.print("  ");
            for (int x = 0; x < imageWidth; x += tileSize) {
                String key = x + "," + y;
                Double time = timeByPosition.get(key);

                if (time != null) {
                    // Normalize: fast (min) -> 0.0 -> ' ', slow (max) -> 1.0 -> '█'
                    double normalized = (time - minTime) / (maxTime - minTime + 0.001);
                    int intensity = (int)(normalized * (intensityChars.length - 1));
                    System.out.print(intensityChars[intensity]);
                } else {
                    System.out.print(" ");
                }
            }
            System.out.println();
        }

        System.out.printf("  Legend: '%s' = %.2fms (min), '%s' = %.2fms (max)%n",
                         intensityChars[0], minTime,
                         intensityChars[intensityChars.length - 1], maxTime);
    }

    /**
     * Print slowest and fastest tasks (no-op if disabled).
     */
    public void printExtremes(int count, TaskFormatter formatter) {
        if (!enabled || records.isEmpty()) return;

        System.out.println();
        System.out.println("SLOWEST " + count + " TASKS:");
        records.stream()
            .sorted((a, b) -> Double.compare(b.getDurationMs(), a.getDurationMs()))
            .limit(count)
            .forEach(r -> System.out.printf("  %s: %.3f ms (thread: %s)%n",
                                           formatter.format(r.metadata),
                                           r.getDurationMs(),
                                           r.threadName));

        System.out.println();
        System.out.println("FASTEST " + count + " TASKS:");
        records.stream()
            .sorted((a, b) -> Double.compare(a.getDurationMs(), b.getDurationMs()))
            .limit(count)
            .forEach(r -> System.out.printf("  %s: %.3f ms (thread: %s)%n",
                                           formatter.format(r.metadata),
                                           r.getDurationMs(),
                                           r.threadName));
    }

    /**
     * Functional interface for extracting position from metadata.
     */
    @FunctionalInterface
    public interface PositionExtractor {
        int[] getPosition(Object metadata);  // Returns [x, y]
    }

    /**
     * Functional interface for formatting task metadata.
     */
    @FunctionalInterface
    public interface TaskFormatter {
        String format(Object metadata);
    }
}
