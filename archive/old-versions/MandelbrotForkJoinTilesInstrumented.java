import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * ForkJoin tile-based implementation with optional instrumentation.
 * Demonstrates work-stealing behavior and task execution variability.
 * Use instrumented=false for production/benchmarking (minimal overhead).
 * Use instrumented=true for detailed statistics and analysis.
 */
public class MandelbrotForkJoinTilesSolution {
    private final int width;
    private final int height;
    private final int maxIterations;
    private final double xMin, xMax, yMin, yMax;
    private BufferedImage image;
    private final boolean instrumented;

    // Execution trackers - can be enabled or disabled
    private final ExecutionTracker tracker;
    private final LocalDequeMonitor dequeMonitor;

    // Track computation time for reporting
    private double lastComputationTimeSeconds = 0;

    public double getLastComputationTime() {
        return lastComputationTimeSeconds;
    }

    public MandelbrotForkJoinTilesSolution(int width, int height, int maxIterations, boolean instrumented) {
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
        this.instrumented = instrumented;
        this.tracker = new ExecutionTracker(instrumented);
        this.dequeMonitor = new LocalDequeMonitor(instrumented);
        this.xMin = -2.5;
        this.xMax = 1.0;
        this.yMin = -1.0;
        this.yMax = 1.0;
    }

    // Backward compatibility: default to instrumented=true
    public MandelbrotForkJoinTilesSolution(int width, int height, int maxIterations) {
        this(width, height, maxIterations, true);
    }

    private static class Tile {
        final int id;
        final int startX, startY;
        final int endX, endY;

        public Tile(int id, int startX, int startY, int endX, int endY) {
            this.id = id;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        public int getWidth() { return endX - startX; }
        public int getHeight() { return endY - startY; }
    }

    /**
     * RecursiveAction that tracks execution time and deque sizes with ExecutionTracker.
     */
    private class TileTask extends RecursiveAction {
        private final Tile tile;

        public TileTask(Tile tile) {
            this.tile = tile;
        }

        @Override
        protected void compute() {
            // Sample local deque size before executing
            dequeMonitor.sample();

            long startTime = System.nanoTime();
            computeTile(tile);
            long endTime = System.nanoTime();

            tracker.record(startTime, endTime, Thread.currentThread().getName(), tile);
        }

        private void computeTile(Tile t) {
            int tileWidth = t.endX - t.startX;
            int tileHeight = t.endY - t.startY;
            int[] pixels = new int[tileWidth * tileHeight];
            int index = 0;

            for (int py = t.startY; py < t.endY; py++) {
                for (int px = t.startX; px < t.endX; px++) {
                    double cx = xMin + (xMax - xMin) * px / width;
                    double cy = yMin + (yMax - yMin) * py / height;

                    double iterations = MandelbrotUtils.computeIterations(cx, cy, maxIterations);
                    int color = MandelbrotUtils.iterationsToColor(iterations, maxIterations);

                    pixels[index++] = color;
                }
            }

            image.setRGB(t.startX, t.startY, tileWidth, tileHeight,
                        pixels, 0, tileWidth);
        }
    }

    public BufferedImage generate(int tileSize) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ForkJoinPool pool = ForkJoinPool.commonPool();

        String mode = instrumented ? "INSTRUMENTED" : "";
        System.out.println(mode + (mode.isEmpty() ? "" : " ") + "ForkJoin Mandelbrot (Pre-Computed Tiles)");
        System.out.println("Image size: " + width + "x" + height);
        System.out.println("Max iterations: " + maxIterations);
        System.out.println("ForkJoinPool parallelism: " + pool.getParallelism());
        System.out.println("Tile size: " + tileSize + "x" + tileSize);
        if (instrumented) {
            System.out.println("Instrumentation: ENABLED (detailed statistics)");
        }
        System.out.println("----------------------------------------");

        // Create all tiles
        List<TileTask> tasks = new ArrayList<>();
        int tileId = 0;
        int tilesX = 0;
        int tilesY = 0;

        for (int y = 0; y < height; y += tileSize) {
            if (y == 0) tilesX = 0;
            for (int x = 0; x < width; x += tileSize) {
                int endX = Math.min(x + tileSize, width);
                int endY = Math.min(y + tileSize, height);

                Tile tile = new Tile(tileId++, x, y, endX, endY);
                tasks.add(new TileTask(tile));

                if (y == 0) tilesX++;
            }
            tilesY++;
        }

        int totalTiles = tasks.size();
        System.out.println("Total tiles: " + totalTiles);

        long startTime = System.nanoTime();

        // Fork and join all tasks using invokeAll (more efficient than fork+join loops)
        ForkJoinTask.invokeAll(tasks);

        long endTime = System.nanoTime();
        lastComputationTimeSeconds = (endTime - startTime) / 1_000_000_000.0;

        // Print detailed statistics only if instrumented
        if (instrumented) {
            tracker.printReport();
            printExtremes(tilesX, tilesY);
            printSpatialHeatmap(tilesX, tilesY, tileSize);
            dequeMonitor.printReport();
        }

        return image;
    }

    private void printStatistics(int tilesX, int tilesY) {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("TASK EXECUTION TIME STATISTICS");
        System.out.println("======================================================================");

        // Calculate statistics
        double[] times = taskTimings.stream()
            .mapToDouble(TaskTiming::getDurationMs)
            .sorted()
            .toArray();

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

        System.out.printf("Total tasks:     %d%n", taskTimings.size());
        System.out.printf("Min time:        %.3f ms%n", minTime);
        System.out.printf("Max time:        %.3f ms%n", maxTime);
        System.out.printf("Average time:    %.3f ms%n", avgTime);
        System.out.printf("Median time:     %.3f ms%n", medianTime);
        System.out.printf("Std deviation:   %.3f ms%n", stdDev);
        System.out.printf("95th percentile: %.3f ms%n", p95);
        System.out.printf("99th percentile: %.3f ms%n", p99);
        System.out.printf("Variability:     %.1fx (max/min ratio)%n", maxTime / minTime);
        System.out.printf("Coefficient of variation: %.1f%%%n", (stdDev / avgTime) * 100);

        // Execution time distribution
        printDistribution(times, minTime, maxTime);

        // Per-thread statistics
        printPerThreadStatistics();

        // Slowest and fastest tiles
        printExtremes();

        // Spatial heatmap
        printSpatialHeatmap(tilesX, tilesY);

        // Load balance analysis
        printLoadBalanceAnalysis();

        // Local deque statistics
        dequeMonitor.printStatistics();
    }

    private void printDistribution(double[] times, double minTime, double maxTime) {
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

            double avgTime = timings.stream()
                .mapToDouble(TaskTiming::getDurationMs)
                .average()
                .orElse(0);

            double totalTime = timings.stream()
                .mapToDouble(TaskTiming::getDurationMs)
                .sum();

            System.out.printf("  %s: %d tasks, avg %.3f ms, total %.3f ms%n",
                             threadName, timings.size(), avgTime, totalTime);
        }
    }

    private void printExtremes() {
        System.out.println();
        System.out.println("SLOWEST 10 TILES:");
        taskTimings.stream()
            .sorted((a, b) -> Double.compare(b.getDurationMs(), a.getDurationMs()))
            .limit(10)
            .forEach(t -> System.out.printf("  Tile #%d at (%d,%d): %.3f ms (thread: %s)%n",
                                           t.tile.id, t.tile.startX, t.tile.startY,
                                           t.getDurationMs(), t.threadName));

        System.out.println();
        System.out.println("FASTEST 10 TILES:");
        taskTimings.stream()
            .sorted((a, b) -> Double.compare(a.getDurationMs(), b.getDurationMs()))
            .limit(10)
            .forEach(t -> System.out.printf("  Tile #%d at (%d,%d): %.3f ms (thread: %s)%n",
                                           t.tile.id, t.tile.startX, t.tile.startY,
                                           t.getDurationMs(), t.threadName));
    }

    private void printSpatialHeatmap(int tilesX, int tilesY) {
        System.out.println();
        System.out.println("SPATIAL DISTRIBUTION OF EXECUTION TIMES:");
        System.out.println("  (Heatmap: darker = longer execution time)");
        System.out.println();

        // Build map of execution times by position
        Map<String, Double> timeByPosition = new HashMap<>();
        double maxTime = 0;
        double minTime = Double.MAX_VALUE;

        for (TaskTiming t : taskTimings) {
            String key = t.tile.startX + "," + t.tile.startY;
            double duration = t.getDurationMs();
            timeByPosition.put(key, duration);
            maxTime = Math.max(maxTime, duration);
            minTime = Math.min(minTime, duration);
        }

        // Print heatmap
        String[] intensityChars = {" ", "░", "▒", "▓", "█"};
        int tileSize = (tilesX > 0) ? width / tilesX : 100;

        for (int y = 0; y < height; y += tileSize) {
            System.out.print("  ");
            for (int x = 0; x < width; x += tileSize) {
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

    private void printLoadBalanceAnalysis() {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("LOAD BALANCE ANALYSIS");
        System.out.println("======================================================================");

        // Group timings by thread
        Map<String, List<TaskTiming>> timingsByThread = new TreeMap<>();
        for (TaskTiming timing : taskTimings) {
            timingsByThread.computeIfAbsent(timing.threadName, k -> new ArrayList<>()).add(timing);
        }

        // Calculate per-thread total times
        double[] threadTotals = timingsByThread.values().stream()
            .mapToDouble(list -> list.stream()
                .mapToDouble(TaskTiming::getDurationMs)
                .sum())
            .toArray();

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
            System.out.println("  Consider using smaller tiles for better granularity.");
        } else {
            System.out.println();
            System.out.println("✓ Good load balance! Work-stealing effectively distributed work.");
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
        int tileSize = 50;

        if (args.length >= 3) {
            width = Integer.parseInt(args[0]);
            height = Integer.parseInt(args[1]);
            maxIterations = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            tileSize = Integer.parseInt(args[3]);
        }

        MandelbrotForkJoinTilesInstrumented mandelbrot =
            new MandelbrotForkJoinTilesInstrumented(width, height, maxIterations);

        long startTime = System.nanoTime();
        BufferedImage image = mandelbrot.generate(tileSize);
        long endTime = System.nanoTime();

        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("%nTotal time: %.3f seconds%n", elapsedSeconds);

        try {
            mandelbrot.saveImage(image, "mandelbrot_forkjoin_instrumented_tile" + tileSize + ".png");
        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
        }
    }
}
