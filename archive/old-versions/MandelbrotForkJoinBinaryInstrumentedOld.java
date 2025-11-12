import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * INSTRUMENTED ForkJoin implementation using BINARY SPLIT.
 * Tracks task execution times and local deque activity during recursive subdivision.
 *
 * This version demonstrates how local deques grow during recursive task creation,
 * contrasting with the pre-computed tiles approach where deques stay empty.
 */
public class MandelbrotForkJoinBinaryInstrumented {
    private final int width;
    private final int height;
    private final int maxIterations;
    private final double xMin, xMax, yMin, yMax;
    private BufferedImage image;

    // Track task execution times
    private final List<TaskTiming> taskTimings = Collections.synchronizedList(new ArrayList<>());

    // Local deque monitor
    private final LocalDequeMonitor dequeMonitor = new LocalDequeMonitor();

    public MandelbrotForkJoinBinaryInstrumented(int width, int height, int maxIterations) {
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
        this.xMin = -2.5;
        this.xMax = 1.0;
        this.yMin = -1.0;
        this.yMax = 1.0;
    }

    private static class Region {
        final int startX, startY;
        final int endX, endY;

        public Region(int startX, int startY, int endX, int endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        public int getWidth() {
            return endX - startX;
        }

        public int getHeight() {
            return endY - startY;
        }

        public int getArea() {
            return getWidth() * getHeight();
        }
    }

    private static class TaskTiming implements TaskStatistics.TaskTiming {
        final Region region;
        final long startNanos;
        final long endNanos;
        final String threadName;
        final boolean wasLeaf;

        public TaskTiming(Region region, long startNanos, long endNanos, String threadName, boolean wasLeaf) {
            this.region = region;
            this.startNanos = startNanos;
            this.endNanos = endNanos;
            this.threadName = threadName;
            this.wasLeaf = wasLeaf;
        }

        @Override
        public double getDurationMs() {
            return (endNanos - startNanos) / 1_000_000.0;
        }

        @Override
        public String getThreadName() {
            return threadName;
        }
    }

    /**
     * Instrumented RecursiveAction that tracks execution time and deque sizes.
     */
    private class MandelbrotTask extends RecursiveAction {
        private final Region region;
        private final int threshold;

        public MandelbrotTask(Region region, int threshold) {
            this.region = region;
            this.threshold = threshold;
        }

        @Override
        protected void compute() {
            String threadName = Thread.currentThread().getName();

            // Sample local deque BEFORE any work
            dequeMonitor.sample();

            long startTime = System.nanoTime();
            boolean isLeaf = false;

            // Base case: region small enough, compute directly
            if (region.getArea() <= threshold) {
                computeRegion(region);
                isLeaf = true;
            } else {
                // Recursive case: BINARY SPLIT along longer dimension
                if (region.getWidth() >= region.getHeight()) {
                    // Split vertically (into left/right)
                    int midX = (region.startX + region.endX) / 2;

                    MandelbrotTask leftTask = new MandelbrotTask(
                        new Region(region.startX, region.startY, midX, region.endY), threshold);
                    MandelbrotTask rightTask = new MandelbrotTask(
                        new Region(midX, region.startY, region.endX, region.endY), threshold);

                    // Sample deque AFTER creating tasks but before fork
                    dequeMonitor.sample();

                    // Fork 1, compute 1 on current thread
                    leftTask.fork();

                    // Sample deque AFTER fork (should see task in deque)
                    dequeMonitor.sample();

                    rightTask.compute();  // Do right half on current thread
                    leftTask.join();      // Wait for left half

                } else {
                    // Split horizontally (into top/bottom)
                    int midY = (region.startY + region.endY) / 2;

                    MandelbrotTask topTask = new MandelbrotTask(
                        new Region(region.startX, region.startY, region.endX, midY), threshold);
                    MandelbrotTask bottomTask = new MandelbrotTask(
                        new Region(region.startX, midY, region.endX, region.endY), threshold);

                    // Sample deque AFTER creating tasks but before fork
                    dequeMonitor.sample();

                    // Fork 1, compute 1 on current thread
                    topTask.fork();

                    // Sample deque AFTER fork
                    dequeMonitor.sample();

                    bottomTask.compute();  // Do bottom half on current thread
                    topTask.join();        // Wait for top half
                }
            }

            long endTime = System.nanoTime();
            taskTimings.add(new TaskTiming(region, startTime, endTime, threadName, isLeaf));

            // Sample deque at end of task
            dequeMonitor.sample();
        }

        private void computeRegion(Region r) {
            int regionWidth = r.endX - r.startX;
            int regionHeight = r.endY - r.startY;
            int[] pixels = new int[regionWidth * regionHeight];
            int index = 0;

            for (int py = r.startY; py < r.endY; py++) {
                for (int px = r.startX; px < r.endX; px++) {
                    double cx = xMin + (xMax - xMin) * px / width;
                    double cy = yMin + (yMax - yMin) * py / height;

                    double iterations = MandelbrotUtils.computeIterations(cx, cy, maxIterations);
                    int color = MandelbrotUtils.iterationsToColor(iterations, maxIterations);

                    pixels[index++] = color;
                }
            }

            image.setRGB(r.startX, r.startY, regionWidth, regionHeight,
                        pixels, 0, regionWidth);
        }
    }

    public BufferedImage generate(int threshold) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ForkJoinPool pool = ForkJoinPool.commonPool();

        System.out.println("INSTRUMENTED ForkJoin Mandelbrot (Binary Split - Recursive)");
        System.out.println("Image size: " + width + "x" + height);
        System.out.println("Max iterations: " + maxIterations);
        System.out.println("ForkJoinPool parallelism: " + pool.getParallelism());
        System.out.println("Threshold: " + threshold + " pixels");
        System.out.println("Split strategy: BINARY (recursive 2-way split)");
        System.out.println("----------------------------------------");

        Region fullImage = new Region(0, 0, width, height);
        MandelbrotTask rootTask = new MandelbrotTask(fullImage, threshold);

        long startTime = System.nanoTime();
        pool.invoke(rootTask);
        long endTime = System.nanoTime();

        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("Generation time: %.3f seconds%n", elapsedSeconds);

        // Print statistics
        printStatistics();

        return image;
    }

    private void printStatistics() {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("TASK EXECUTION TIME STATISTICS");
        System.out.println("======================================================================");

        // Separate leaf tasks (actual computation) from internal tasks (coordination)
        List<TaskTiming> leafTasks = new ArrayList<>();
        List<TaskTiming> internalTasks = new ArrayList<>();

        for (TaskTiming timing : taskTimings) {
            if (timing.wasLeaf) {
                leafTasks.add(timing);
            } else {
                internalTasks.add(timing);
            }
        }

        System.out.printf("Total tasks:     %d (%d leaf tasks, %d internal tasks)%n",
                         taskTimings.size(), leafTasks.size(), internalTasks.size());

        // Analyze leaf tasks (the actual work) using TaskStatistics utility
        if (!leafTasks.isEmpty()) {
            System.out.println();
            System.out.println("LEAF TASK STATISTICS (actual computation):");

            double[] times = leafTasks.stream()
                .mapToDouble(TaskTiming::getDurationMs)
                .sorted()
                .toArray();

            double minTime = times[0];
            double maxTime = times[times.length - 1];
            double avgTime = Arrays.stream(times).average().orElse(0);
            double medianTime = times[times.length / 2];

            System.out.printf("  Min time:        %.3f ms%n", minTime);
            System.out.printf("  Max time:        %.3f ms%n", maxTime);
            System.out.printf("  Average time:    %.3f ms%n", avgTime);
            System.out.printf("  Median time:     %.3f ms%n", medianTime);
            System.out.printf("  Variability:     %.1fx (max/min ratio)%n", maxTime / minTime);
        }

        // Use TaskStatistics for common analysis
        printPerThreadStatistics();

        // Load balance analysis using TaskStatistics
        TaskStatistics.printLoadBalanceAnalysis(taskTimings);

        // Local deque statistics - shows recursive task creation behavior
        dequeMonitor.printStatistics();
    }

    private void printPerThreadStatistics() {
        System.out.println();
        System.out.println("PER-THREAD STATISTICS:");

        Map<String, List<TaskTiming>> timingsByThread = new TreeMap<>();
        for (TaskTiming timing : taskTimings) {
            timingsByThread.computeIfAbsent(timing.threadName, k -> new ArrayList<>()).add(timing);
        }

        for (Map.Entry<String, List<TaskTiming>> entry : timingsByThread.entrySet()) {
            String threadName = entry.getKey();
            List<TaskTiming> timings = entry.getValue();

            long leafCount = timings.stream().filter(t -> t.wasLeaf).count();
            long internalCount = timings.size() - leafCount;

            double totalTime = timings.stream()
                .mapToDouble(TaskTiming::getDurationMs)
                .sum();

            System.out.printf("  %s: %d tasks (%d leaf, %d internal), total %.3f ms%n",
                             threadName, timings.size(), leafCount, internalCount, totalTime);
        }
    }

    public void saveImage(BufferedImage image, String filename) throws IOException {
        File outputFile = new File(filename);
        ImageIO.write(image, "PNG", outputFile);
        System.out.println();
        System.out.println("Image saved to: " + filename);
    }

    public static void main(String[] args) {
        int width = 1600;
        int height = 1200;
        int maxIterations = 2000;
        int threshold = 5000;

        if (args.length >= 3) {
            width = Integer.parseInt(args[0]);
            height = Integer.parseInt(args[1]);
            maxIterations = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            threshold = Integer.parseInt(args[3]);
        }

        MandelbrotForkJoinBinaryInstrumented mandelbrot =
            new MandelbrotForkJoinBinaryInstrumented(width, height, maxIterations);

        long startTime = System.nanoTime();
        BufferedImage image = mandelbrot.generate(threshold);
        long endTime = System.nanoTime();

        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("%nTotal time: %.3f seconds%n", elapsedSeconds);

        try {
            mandelbrot.saveImage(image, "mandelbrot_forkjoin_binary_instrumented_threshold" + threshold + ".png");
        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
        }
    }
}
