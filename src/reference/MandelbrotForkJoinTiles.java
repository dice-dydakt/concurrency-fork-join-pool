import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * ForkJoin implementation using pre-computed tiles with optional instrumentation.
 * Demonstrates work-stealing with tiles (vs binary splitting).
 * Use instrumented=false for production/benchmarking (minimal overhead).
 * Use instrumented=true for detailed statistics and analysis.
 */
public class MandelbrotForkJoinTiles {
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

    public MandelbrotForkJoinTiles(int width, int height, int maxIterations, boolean instrumented) {
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
    public MandelbrotForkJoinTiles(int width, int height, int maxIterations) {
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
     * RecursiveAction that processes a single tile.
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

            // Write pixels to image
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
            dequeMonitor.printStatistics();
        }

        return image;
    }

    private void printExtremes(int tilesX, int tilesY) {
        System.out.println();
        tracker.printExtremes(10, metadata -> {
            if (metadata instanceof Tile) {
                Tile t = (Tile) metadata;
                return String.format("Tile #%d at (%d,%d)", t.id, t.startX, t.startY);
            }
            return "Unknown";
        });
    }

    private void printSpatialHeatmap(int tilesX, int tilesY, int tileSize) {
        tracker.printSpatialHeatmap(width, height, tileSize, metadata -> {
            if (metadata instanceof Tile) {
                Tile t = (Tile) metadata;
                return new int[]{t.startX, t.startY};
            }
            return new int[]{0, 0};
        });
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
        boolean instrumented = false;  // Default: no detailed statistics

        // Check for --instrumented flag first (can be at any position)
        for (String arg : args) {
            if (arg.equals("--instrumented")) {
                instrumented = true;
                break;
            }
        }

        // Parse numeric arguments (skip --instrumented if present)
        int argIndex = 0;
        if (argIndex < args.length && !args[argIndex].equals("--instrumented")) {
            width = Integer.parseInt(args[argIndex++]);
        }
        if (argIndex < args.length && !args[argIndex].equals("--instrumented")) {
            height = Integer.parseInt(args[argIndex++]);
        }
        if (argIndex < args.length && !args[argIndex].equals("--instrumented")) {
            maxIterations = Integer.parseInt(args[argIndex++]);
        }
        if (argIndex < args.length && !args[argIndex].equals("--instrumented")) {
            tileSize = Integer.parseInt(args[argIndex++]);
        }

        MandelbrotForkJoinTiles mandelbrot =
            new MandelbrotForkJoinTiles(width, height, maxIterations, instrumented);

        long totalStartTime = System.nanoTime();
        BufferedImage image = mandelbrot.generate(tileSize);

        try {
            mandelbrot.saveImage(image, "mandelbrot_forkjoin_tiles" + tileSize + ".png");
        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
        }

        long totalEndTime = System.nanoTime();
        double totalSeconds = (totalEndTime - totalStartTime) / 1_000_000_000.0;

        // Always report timing at the very end
        System.out.println();
        System.out.printf("Computation time: %.3f seconds%n", mandelbrot.getLastComputationTime());
        System.out.printf("Total time:       %.3f seconds%n", totalSeconds);
    }
}
