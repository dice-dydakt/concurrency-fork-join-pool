import java.awt.image.BufferedImage;

/**
 * STUDENT TOOL: Compare ForkJoin performance with different tile sizes.
 *
 * This tool helps you explore the impact of task granularity on work-stealing benefit.
 *
 * Usage: java CompareTileSizes <width> <height> <maxIter> [tileSizes...]
 * Example: java CompareTileSizes 1600 1200 2000 25 50 75 100 150 200
 */
public class CompareTileSizes {

    public static void main(String[] args) {
        int width = 1600;
        int height = 1200;
        int maxIterations = 2000;
        int warmupRuns = 2;
        int benchmarkRuns = 3;

        if (args.length < 3) {
            System.out.println("Usage: java CompareTileSizes <width> <height> <maxIter> [tileSizes...]");
            System.out.println("Example: java CompareTileSizes 1600 1200 2000 25 50 75 100 150 200");
            System.exit(1);
        }

        width = Integer.parseInt(args[0]);
        height = Integer.parseInt(args[1]);
        maxIterations = Integer.parseInt(args[2]);

        // Default tile sizes if not specified
        int[] tileSizes;
        if (args.length > 3) {
            tileSizes = new int[args.length - 3];
            for (int i = 0; i < tileSizes.length; i++) {
                tileSizes[i] = Integer.parseInt(args[3 + i]);
            }
        } else {
            tileSizes = new int[]{25, 50, 75, 100, 150, 200};
        }

        System.out.println("Tile Size Performance Comparison");
        System.out.println("=================================");
        System.out.println("Image size: " + width + "x" + height);
        System.out.println("Max iterations: " + maxIterations);
        System.out.println("Tile sizes to test: " + java.util.Arrays.toString(tileSizes));
        System.out.println("Warmup runs: " + warmupRuns);
        System.out.println("Benchmark runs: " + benchmarkRuns);
        System.out.println();

        // Create instance
        MandelbrotForkJoinTiles mandelbrot =
            new MandelbrotForkJoinTiles(width, height, maxIterations);

        // Warmup
        System.out.println("Warming up JVM...");
        for (int tileSize : tileSizes) {
            for (int i = 0; i < warmupRuns; i++) {
                mandelbrot.generate(tileSize);
            }
        }
        System.out.println("Warmup complete.\n");

        // Results storage
        double[] avgTimes = new double[tileSizes.length];
        double[] minTimes = new double[tileSizes.length];
        double[] maxTimes = new double[tileSizes.length];
        int[] taskCounts = new int[tileSizes.length];

        // Benchmark each tile size
        for (int i = 0; i < tileSizes.length; i++) {
            int tileSize = tileSizes[i];
            System.out.println("Benchmarking tile size: " + tileSize + "x" + tileSize);

            long[] times = new long[benchmarkRuns];
            for (int run = 0; run < benchmarkRuns; run++) {
                long start = System.nanoTime();
                BufferedImage img = mandelbrot.generate(tileSize);
                long end = System.nanoTime();
                times[run] = end - start;
                System.out.printf("  Run %d: %.3f seconds%n", run + 1, times[run] / 1_000_000_000.0);
            }

            // Calculate statistics
            avgTimes[i] = average(times);
            minTimes[i] = min(times);
            maxTimes[i] = max(times);

            // Calculate task count
            int tilesX = (width + tileSize - 1) / tileSize;
            int tilesY = (height + tileSize - 1) / tileSize;
            taskCounts[i] = tilesX * tilesY;

            System.out.println();
        }

        // Find best tile size
        int bestIndex = 0;
        double bestTime = avgTimes[0];
        for (int i = 1; i < avgTimes.length; i++) {
            if (avgTimes[i] < bestTime) {
                bestTime = avgTimes[i];
                bestIndex = i;
            }
        }

        // Print summary table
        System.out.println("========================================");
        System.out.println("RESULTS SUMMARY");
        System.out.println("========================================");
        System.out.println();
        System.out.printf("%-10s %-10s %-10s %-10s %-10s %-10s%n",
            "TileSize", "Tasks", "Avg(s)", "Min(s)", "Max(s)", "Status");
        System.out.println("---------------------------------------------------------------");

        for (int i = 0; i < tileSizes.length; i++) {
            String status = (i == bestIndex) ? "** BEST **" : "";
            System.out.printf("%-10d %-10d %-10.3f %-10.3f %-10.3f %-10s%n",
                tileSizes[i],
                taskCounts[i],
                avgTimes[i],
                minTimes[i],
                maxTimes[i],
                status);
        }

        System.out.println();
        System.out.println("KEY OBSERVATIONS:");
        System.out.println("  Best tile size: " + tileSizes[bestIndex] + "x" + tileSizes[bestIndex]);
        System.out.printf("  Best time: %.3f seconds%n", bestTime);
        System.out.println("  Number of tasks: " + taskCounts[bestIndex]);

        System.out.println();
        System.out.println("ANALYSIS QUESTIONS:");
        System.out.println("  1. Why does performance vary with tile size?");
        System.out.println("  2. What happens with very small tiles (high task count)?");
        System.out.println("  3. What happens with very large tiles (low task count)?");
        System.out.println("  4. How does work-stealing benefit change with tile size?");
        System.out.println();
        System.out.println("See docs/WORK_STEALING_ANALYSIS.md for detailed answers!");
    }

    private static double average(long[] times) {
        long sum = 0;
        for (long t : times) sum += t;
        return sum / (double) times.length / 1_000_000_000.0;
    }

    private static double min(long[] times) {
        long min = Long.MAX_VALUE;
        for (long t : times) if (t < min) min = t;
        return min / 1_000_000_000.0;
    }

    private static double max(long[] times) {
        long max = Long.MIN_VALUE;
        for (long t : times) if (t > max) max = t;
        return max / 1_000_000_000.0;
    }
}
