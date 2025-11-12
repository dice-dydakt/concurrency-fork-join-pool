import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Instrumented tile-based implementation that measures execution time per task.
 * Demonstrates task execution variability and its impact on load balancing.
 */
public class MandelbrotTileBasedInstrumented {
    private final int width;
    private final int height;
    private final int maxIterations;
    private final double xMin, xMax, yMin, yMax;

    // Track task execution times
    private final List<TaskTiming> taskTimings = Collections.synchronizedList(new ArrayList<>());

    public MandelbrotTileBasedInstrumented(int width, int height, int maxIterations) {
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
        this.xMin = -2.5;
        this.xMax = 1.0;
        this.yMin = -1.0;
        this.yMax = 1.0;
    }

    private static class Tile {
        final int startX, startY;
        final int endX, endY;

        public Tile(int startX, int startY, int endX, int endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        public int getWidth() { return endX - startX; }
        public int getHeight() { return endY - startY; }
    }

    private static class TileResult {
        final Tile tile;
        final int[] pixelData;

        public TileResult(Tile tile, int[] pixelData) {
            this.tile = tile;
            this.pixelData = pixelData;
        }
    }

    private static class TaskTiming {
        final int tileId;
        final Tile tile;
        final long startTime;
        final long endTime;
        final String threadName;

        public TaskTiming(int tileId, Tile tile, long startTime, long endTime, String threadName) {
            this.tileId = tileId;
            this.tile = tile;
            this.startTime = startTime;
            this.endTime = endTime;
            this.threadName = threadName;
        }

        public double getDurationMs() {
            return (endTime - startTime) / 1_000_000.0;
        }
    }

    private class TileTask implements Callable<TileResult> {
        private final Tile tile;
        private final int tileId;

        public TileTask(Tile tile, int tileId) {
            this.tile = tile;
            this.tileId = tileId;
        }

        @Override
        public TileResult call() {
            long startTime = System.nanoTime();
            String threadName = Thread.currentThread().getName();

            int tileWidth = tile.getWidth();
            int tileHeight = tile.getHeight();
            int[] pixels = new int[tileWidth * tileHeight];
            int index = 0;

            for (int py = tile.startY; py < tile.endY; py++) {
                for (int px = tile.startX; px < tile.endX; px++) {
                    double cx = xMin + (xMax - xMin) * px / width;
                    double cy = yMin + (yMax - yMin) * py / height;

                    double iterations = MandelbrotUtils.computeIterations(cx, cy, maxIterations);
                    pixels[index++] = MandelbrotUtils.iterationsToColor(iterations, maxIterations);
                }
            }

            long endTime = System.nanoTime();
            taskTimings.add(new TaskTiming(tileId, tile, startTime, endTime, threadName));

            return new TileResult(tile, pixels);
        }
    }

    public BufferedImage generate(int numThreads, int tileSize) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            // Create tiles
            List<Tile> tiles = new ArrayList<>();
            for (int y = 0; y < height; y += tileSize) {
                for (int x = 0; x < width; x += tileSize) {
                    int endX = Math.min(x + tileSize, width);
                    int endY = Math.min(y + tileSize, height);
                    tiles.add(new Tile(x, y, endX, endY));
                }
            }

            System.out.println("Total tiles: " + tiles.size());

            // Use ExecutorCompletionService for completion-order processing
            ExecutorCompletionService<TileResult> completionService =
                new ExecutorCompletionService<>(executor);

            // Submit all tasks
            int tileId = 0;
            for (Tile tile : tiles) {
                completionService.submit(new TileTask(tile, tileId++));
            }

            // Collect results
            for (int i = 0; i < tiles.size(); i++) {
                TileResult result = completionService.take().get();
                Tile tile = result.tile;

                image.setRGB(tile.startX, tile.startY,
                           tile.getWidth(), tile.getHeight(),
                           result.pixelData, 0, tile.getWidth());
            }

        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error during parallel computation: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }

        return image;
    }

    public void printStatistics() {
        if (taskTimings.isEmpty()) {
            System.out.println("No timing data collected.");
            return;
        }

        // Calculate statistics
        double[] durations = taskTimings.stream()
            .mapToDouble(TaskTiming::getDurationMs)
            .sorted()
            .toArray();

        double min = durations[0];
        double max = durations[durations.length - 1];
        double avg = Arrays.stream(durations).average().orElse(0);
        double median = durations[durations.length / 2];
        double p95 = durations[(int)(durations.length * 0.95)];
        double p99 = durations[(int)(durations.length * 0.99)];

        // Standard deviation
        double variance = Arrays.stream(durations)
            .map(d -> Math.pow(d - avg, 2))
            .average()
            .orElse(0);
        double stdDev = Math.sqrt(variance);

        System.out.println("\n" + "=".repeat(70));
        System.out.println("TASK EXECUTION TIME STATISTICS");
        System.out.println("=".repeat(70));
        System.out.printf("Total tasks:     %d%n", taskTimings.size());
        System.out.printf("Min time:        %.3f ms%n", min);
        System.out.printf("Max time:        %.3f ms%n", max);
        System.out.printf("Average time:    %.3f ms%n", avg);
        System.out.printf("Median time:     %.3f ms%n", median);
        System.out.printf("Std deviation:   %.3f ms%n", stdDev);
        System.out.printf("95th percentile: %.3f ms%n", p95);
        System.out.printf("99th percentile: %.3f ms%n", p99);
        System.out.printf("Variability:     %.1fx (max/min ratio)%n", max / min);
        System.out.printf("Coefficient of variation: %.1f%%%n", (stdDev / avg) * 100);

        // Histogram
        System.out.println("\nEXECUTION TIME DISTRIBUTION:");
        printHistogram(durations);

        // Per-thread statistics
        System.out.println("\nPER-THREAD STATISTICS:");
        printPerThreadStats();

        // Find slowest tiles
        System.out.println("\nSLOWEST 10 TILES:");
        taskTimings.stream()
            .sorted((a, b) -> Double.compare(b.getDurationMs(), a.getDurationMs()))
            .limit(10)
            .forEach(t -> System.out.printf("  Tile #%d at (%d,%d): %.3f ms (thread: %s)%n",
                t.tileId, t.tile.startX, t.tile.startY, t.getDurationMs(), t.threadName));

        // Find fastest tiles
        System.out.println("\nFASTEST 10 TILES:");
        taskTimings.stream()
            .sorted(Comparator.comparingDouble(TaskTiming::getDurationMs))
            .limit(10)
            .forEach(t -> System.out.printf("  Tile #%d at (%d,%d): %.3f ms (thread: %s)%n",
                t.tileId, t.tile.startX, t.tile.startY, t.getDurationMs(), t.threadName));

        // Spatial visualization
        System.out.println("\nSPATIAL DISTRIBUTION OF EXECUTION TIMES:");
        printSpatialHeatmap();

        // Impact analysis
        System.out.println("\nLOAD BALANCING IMPACT:");
        analyzeLoadBalancing(durations, avg);
    }

    private void printHistogram(double[] durations) {
        int bins = 20;
        double min = durations[0];
        double max = durations[durations.length - 1];
        double binSize = (max - min) / bins;

        int[] counts = new int[bins];
        for (double d : durations) {
            int bin = Math.min((int)((d - min) / binSize), bins - 1);
            counts[bin]++;
        }

        int maxCount = Arrays.stream(counts).max().orElse(1);
        for (int i = 0; i < bins; i++) {
            double rangeStart = min + i * binSize;
            double rangeEnd = rangeStart + binSize;
            int barLength = (int)(40.0 * counts[i] / maxCount);
            String bar = "█".repeat(barLength);
            System.out.printf("  %5.1f-%5.1fms [%4d]: %s%n",
                rangeStart, rangeEnd, counts[i], bar);
        }
    }

    private void printPerThreadStats() {
        Map<String, List<TaskTiming>> byThread = new HashMap<>();
        for (TaskTiming t : taskTimings) {
            byThread.computeIfAbsent(t.threadName, k -> new ArrayList<>()).add(t);
        }

        byThread.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> {
                String thread = entry.getKey();
                List<TaskTiming> tasks = entry.getValue();
                double totalTime = tasks.stream().mapToDouble(TaskTiming::getDurationMs).sum();
                double avgTime = totalTime / tasks.size();
                System.out.printf("  %s: %d tasks, avg %.3f ms, total %.3f ms%n",
                    thread, tasks.size(), avgTime, totalTime);
            });
    }

    private void printSpatialHeatmap() {
        // Build a 2D map of execution times
        Map<String, Double> timeByPosition = new HashMap<>();
        double maxTime = 0;
        double minTime = Double.MAX_VALUE;

        int maxX = 0, maxY = 0;
        for (TaskTiming t : taskTimings) {
            String key = t.tile.startX + "," + t.tile.startY;
            double duration = t.getDurationMs();
            timeByPosition.put(key, duration);
            maxTime = Math.max(maxTime, duration);
            minTime = Math.min(minTime, duration);
            maxX = Math.max(maxX, t.tile.startX);
            maxY = Math.max(maxY, t.tile.startY);
        }

        // Determine tile size from first task
        int tileSize = taskTimings.isEmpty() ? 50 : taskTimings.get(0).tile.getWidth();

        // Print heatmap using ASCII characters
        String[] intensityChars = {" ", "░", "▒", "▓", "█"};

        System.out.println("  (Heatmap: darker = longer execution time)");
        System.out.println();

        for (int y = 0; y <= maxY; y += tileSize) {
            System.out.print("  ");
            for (int x = 0; x <= maxX; x += tileSize) {
                String key = x + "," + y;
                Double time = timeByPosition.get(key);

                if (time == null) {
                    System.out.print(" ");
                } else {
                    // Normalize time to 0-1 range
                    double normalized = (time - minTime) / (maxTime - minTime);
                    int intensity = (int)(normalized * (intensityChars.length - 1));
                    System.out.print(intensityChars[intensity]);
                }
            }
            System.out.println();
        }

        System.out.printf("  Legend: ' ' = %.2fms, '█' = %.2fms%n", minTime, maxTime);
    }

    private void analyzeLoadBalancing(double[] durations, double avg) {
        // Group timings by thread to calculate per-thread total times
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

        System.out.printf("  Thread total times: min=%.3f ms, max=%.3f ms, avg=%.3f ms%n",
                         minThreadTime, maxThreadTime, avgThreadTime);

        double imbalance = maxThreadTime - minThreadTime;
        double efficiency = (minThreadTime / maxThreadTime) * 100;

        System.out.printf("  Time imbalance: %.3f ms (%.1f%% of max)%n",
                         imbalance, (imbalance / maxThreadTime) * 100);
        System.out.printf("  Load balance efficiency: %.1f%%%n", efficiency);

        if (efficiency < 70) {
            System.out.println("\n  ⚠ Poor load balancing detected!");
            System.out.println("  Recommendation: Use smaller tiles or work-stealing executor");
        } else if (efficiency > 95) {
            System.out.println("\n  ✓ Excellent load balancing!");
        } else {
            System.out.println("\n  ○ Good load balancing.");
        }
    }

    public void saveImage(BufferedImage image, String filename) throws IOException {
        File outputFile = new File(filename);
        ImageIO.write(image, "PNG", outputFile);
        System.out.println("Image saved to: " + filename);
    }

    public static void main(String[] args) {
        int width = 1600;
        int height = 1200;
        int maxIterations = 2000;
        int numThreads = Runtime.getRuntime().availableProcessors();
        int tileSize = 50;

        if (args.length >= 5) {
            width = Integer.parseInt(args[0]);
            height = Integer.parseInt(args[1]);
            maxIterations = Integer.parseInt(args[2]);
            numThreads = Integer.parseInt(args[3]);
            tileSize = Integer.parseInt(args[4]);
        }

        System.out.println("INSTRUMENTED Tile-Based Mandelbrot Generation");
        System.out.println("Image size: " + width + "x" + height);
        System.out.println("Max iterations: " + maxIterations);
        System.out.println("Number of threads: " + numThreads);
        System.out.println("Tile size: " + tileSize + "x" + tileSize);
        System.out.println("----------------------------------------");

        MandelbrotTileBasedInstrumented mandelbrot =
            new MandelbrotTileBasedInstrumented(width, height, maxIterations);

        long startTime = System.nanoTime();
        BufferedImage image = mandelbrot.generate(numThreads, tileSize);
        long endTime = System.nanoTime();

        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("Generation time: %.3f seconds%n", elapsedSeconds);

        // Print detailed statistics
        mandelbrot.printStatistics();

        try {
            mandelbrot.saveImage(image, "mandelbrot_instrumented_tile" + tileSize + ".png");
        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
        }
    }
}
