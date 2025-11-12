import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Field;

/**
 * Utility for monitoring ForkJoinPool worker thread local deque sizes.
 * Uses reflection to access internal ForkJoinWorkerThread queue state.
 *
 * Note: This uses reflection on JDK internal APIs and may break in future Java versions.
 * Requires: --add-opens java.base/java.util.concurrent=ALL-UNNAMED
 */
public class LocalDequeMonitor {

    /**
     * A sample of a worker's local deque size at a specific point in time.
     */
    public static class DequeSample {
        public final String threadName;
        public final int queueLength;
        public final long timestampNanos;

        public DequeSample(String threadName, int queueLength, long timestampNanos) {
            this.threadName = threadName;
            this.queueLength = queueLength;
            this.timestampNanos = timestampNanos;
        }
    }

    private final List<DequeSample> samples;
    private final boolean enabled;

    /**
     * Create a deque monitor.
     * @param enabled If false, sampling is a no-op
     */
    public LocalDequeMonitor(boolean enabled) {
        this.enabled = enabled;
        this.samples = enabled ? Collections.synchronizedList(new ArrayList<>()) : null;
    }

    /**
     * Create an enabled deque monitor (for backward compatibility).
     */
    public LocalDequeMonitor() {
        this(true);
    }

    /**
     * Sample the current thread's local deque size (no-op if disabled).
     * Should be called from within a RecursiveAction/Task compute() method.
     */
    public void sample() {
        if (!enabled) return;

        Thread currentThread = Thread.currentThread();
        int queueSize = getLocalQueueLength(currentThread);

        if (queueSize >= 0) {
            samples.add(new DequeSample(
                currentThread.getName(),
                queueSize,
                System.nanoTime()
            ));
        }
    }

    /**
     * Get all collected samples.
     */
    public List<DequeSample> getSamples() {
        return enabled ? new ArrayList<>(samples) : Collections.emptyList();
    }

    /**
     * Clear all collected samples (no-op if disabled).
     */
    public void clear() {
        if (enabled) {
            samples.clear();
        }
    }

    /**
     * Print statistics about local deque activity (no-op if disabled).
     */
    public void printStatistics() {
        if (!enabled) return;  // No-op if disabled

        if (samples.isEmpty()) {
            System.out.println();
            System.out.println("LOCAL DEQUE STATISTICS:");
            System.out.println("(No samples collected - reflection may have failed)");
            System.out.println();
            System.out.println("To enable local deque tracking, run with:");
            System.out.println("  java --add-opens java.base/java.util.concurrent=ALL-UNNAMED ...");
            return;
        }

        System.out.println();
        System.out.println("======================================================================");
        System.out.println("LOCAL DEQUE STATISTICS");
        System.out.println("======================================================================");

        // Group samples by thread
        Map<String, List<DequeSample>> samplesByThread = new TreeMap<>();
        for (DequeSample sample : samples) {
            samplesByThread.computeIfAbsent(sample.threadName, k -> new ArrayList<>()).add(sample);
        }

        System.out.printf("Collected %d samples from %d worker threads%n%n",
                         samples.size(), samplesByThread.size());

        // Print per-thread statistics
        for (Map.Entry<String, List<DequeSample>> entry : samplesByThread.entrySet()) {
            String threadName = entry.getKey();
            List<DequeSample> threadSamples = entry.getValue();

            int maxQueue = threadSamples.stream().mapToInt(s -> s.queueLength).max().orElse(0);
            double avgQueue = threadSamples.stream().mapToInt(s -> s.queueLength).average().orElse(0);
            int minQueue = threadSamples.stream().mapToInt(s -> s.queueLength).min().orElse(0);

            System.out.printf("%s:%n", threadName);
            System.out.printf("  Samples: %d, Queue: min=%d, max=%d, avg=%.1f%n",
                             threadSamples.size(), minQueue, maxQueue, avgQueue);

            // Show timeline (sample subset to fit display)
            int displayWidth = 50;
            int step = Math.max(1, threadSamples.size() / displayWidth);
            StringBuilder timeline = new StringBuilder("    ");

            for (int i = 0; i < threadSamples.size(); i += step) {
                int queueSize = threadSamples.get(i).queueLength;
                if (maxQueue == 0) {
                    timeline.append(" ");
                } else if (queueSize == 0) {
                    timeline.append("·");
                } else if (queueSize < maxQueue * 0.25) {
                    timeline.append("░");
                } else if (queueSize < maxQueue * 0.5) {
                    timeline.append("▒");
                } else if (queueSize < maxQueue * 0.75) {
                    timeline.append("▓");
                } else {
                    timeline.append("█");
                }
            }

            System.out.println(timeline.toString());
            System.out.println();
        }

        System.out.println("Interpretation:");
        System.out.println("  · = Empty deque (thread stealing from others)");
        System.out.println("  ░▒▓█ = Deque has tasks (darker = more tasks)");
        System.out.println();
        System.out.println("Note: Local deques fill up when workers fork sub-tasks.");
        System.out.println("      In pre-computed patterns, deques stay empty (all tasks");
        System.out.println("      are forked by main thread → external submission queue).");
    }

    /**
     * Get the local queue length for a ForkJoinWorkerThread using reflection.
     * Returns -1 if unable to retrieve (not a worker thread or reflection fails).
     */
    private static int getLocalQueueLength(Thread thread) {
        if (!(thread instanceof ForkJoinWorkerThread)) {
            return -1;
        }

        try {
            ForkJoinWorkerThread worker = (ForkJoinWorkerThread) thread;

            // Access the workQueue field
            Field workQueueField = ForkJoinWorkerThread.class.getDeclaredField("workQueue");
            workQueueField.setAccessible(true);
            Object queue = workQueueField.get(worker);

            if (queue == null) {
                return 0;
            }

            // Access base and top fields to calculate queue size
            Class<?> queueClass = queue.getClass();
            Field baseField = queueClass.getDeclaredField("base");
            Field topField = queueClass.getDeclaredField("top");
            baseField.setAccessible(true);
            topField.setAccessible(true);

            int base = baseField.getInt(queue);
            int top = topField.getInt(queue);

            return Math.max(0, top - base);
        } catch (Exception e) {
            // Reflection failed - expected in some JVM configurations
            return -1;
        }
    }
}
