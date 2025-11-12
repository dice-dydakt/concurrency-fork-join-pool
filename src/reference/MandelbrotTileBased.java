import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Tile-based ThreadPool implementation with optional instrumentation using ExecutionTracker.
 * Use instrumented=false for production/benchmarking (minimal overhead).
 * Use instrumented=true for detailed statistics and analysis.
 */
public class MandelbrotTileBased {
    private final int width;
    private final int height;
    private final int maxIterations;
    private final double xMin, xMax, yMin, yMax;
    private final boolean instrumented;

    // Execution tracker - can be enabled or disabled
    private final ExecutionTracker tracker;

    public MandelbrotTileBased(int width, int height, int maxIterations, boolean instrumented) {
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
        this.instrumented = instrumented;
        this.tracker = new ExecutionTracker(instrumented);
        this.xMin = -2.5;
        this.xMax = 1.0;
        this.yMin = -1.0;
        this.yMax = 1.0;
    }

    public MandelbrotTileBased(int width, int height, int maxIterations) {
        this(width, height, maxIterations, true);
    }

    private static class Tile {
        final int startX, startY;
        final int endX, endY;
        final int id;

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

    private static class TileResult {
        final Tile tile;
        final int[] pixelData;

        public TileResult(Tile tile, int[] pixelData) {
            this.tile = tile;
            this.pixelData = pixelData;
        }
    }

    private class TileTask implements Callable<TileResult> {
        private final Tile tile;

        public TileTask(Tile tile) {
            this.tile = tile;
        }

        @Override
        public TileResult call() {
            long startTime = System.nanoTime();  // ← Instrumentation line 1

            int tileWidth = tile.getWidth();
            int tileHeight = tile.getHeight();
            int[] pixels = new int[tileWidth * tileHeight];
            int index = 0;

            for (int py = tile.startY; py < tile.endY; py++) {
                for (int px = tile.startX; px < tile.endX; px++) {
                    double cx = xMin + (xMax - xMin) * px / width;
                    double cy = yMin + (yMax - yMin) * py / height;

                    double iterations = MandelbrotUtils.computeIterations(cx, cy, maxIterations);
                    int color = MandelbrotUtils.iterationsToColor(iterations, maxIterations);

                    pixels[index++] = color;
                }
            }

            long endTime = System.nanoTime();  // ← Instrumentation line 2
            tracker.record(startTime, endTime, Thread.currentThread().getName(), tile);  // ← Instrumentation line 3

            return new TileResult(tile, pixels);
        }
    }

    // Track computation time for reporting
    private double lastComputationTimeSeconds = 0;

    public double getLastComputationTime() {
        return lastComputationTimeSeconds;
    }

    public BufferedImage generate(int numThreads, int tileSize) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<TileResult> completionService = new ExecutorCompletionService<>(executor);

        String mode = instrumented ? "INSTRUMENTED" : "";
        System.out.println(mode + (mode.isEmpty() ? "" : " ") + "Tile-Based Mandelbrot Generation");
        System.out.println("Image size: " + width + "x" + height);
        System.out.println("Max iterations: " + maxIterations);
        System.out.println("Number of threads: " + numThreads);
        System.out.println("Tile size: " + tileSize + "x" + tileSize);
        System.out.println("Result processing: ExecutorCompletionService (completion order)");
        if (instrumented) {
            System.out.println("Instrumentation: ENABLED (detailed statistics)");
        }
        System.out.println("----------------------------------------");

        try {
            // Create all tiles
            int tilesX = 0;
            int tilesY = 0;
            int tileId = 0;
            int totalTiles = 0;

            for (int y = 0; y < height; y += tileSize) {
                if (y == 0) tilesY = (height + tileSize - 1) / tileSize;
                tilesX = 0;

                for (int x = 0; x < width; x += tileSize) {
                    if (y == 0) tilesX++;

                    int endX = Math.min(x + tileSize, width);
                    int endY = Math.min(y + tileSize, height);

                    completionService.submit(new TileTask(new Tile(tileId++, x, y, endX, endY)));
                    totalTiles++;
                }
            }

            System.out.println("Total tiles: " + totalTiles);

            long startTime = System.nanoTime();

            // Collect results in completion order and write to image
            for (int i = 0; i < totalTiles; i++) {
                TileResult result = completionService.take().get();
                Tile tile = result.tile;
                image.setRGB(tile.startX, tile.startY, tile.getWidth(), tile.getHeight(),
                           result.pixelData, 0, tile.getWidth());
            }

            long endTime = System.nanoTime();
            lastComputationTimeSeconds = (endTime - startTime) / 1_000_000_000.0;

            // Print detailed statistics only if instrumented
            if (instrumented) {
                tracker.printReport();
                printExtremes();
                printSpatialHeatmap(tilesX, tilesY, tileSize);
            }

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        return image;
    }

    private void printExtremes() {
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
        int numThreads = Runtime.getRuntime().availableProcessors();
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
            numThreads = Integer.parseInt(args[argIndex++]);
        }
        if (argIndex < args.length && !args[argIndex].equals("--instrumented")) {
            tileSize = Integer.parseInt(args[argIndex++]);
        }

        MandelbrotTileBased mandelbrot =
            new MandelbrotTileBased(width, height, maxIterations, instrumented);

        long totalStartTime = System.nanoTime();
        BufferedImage image = mandelbrot.generate(numThreads, tileSize);

        try {
            mandelbrot.saveImage(image, "mandelbrot_instrumented_tile" + tileSize + ".png");
        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
        }

        long totalEndTime = System.nanoTime();
        double totalSeconds = (totalEndTime - totalStartTime) / 1_000_000_000.0;

        System.out.println();
        System.out.printf("Computation time: %.3f seconds%n", mandelbrot.getLastComputationTime());
        System.out.printf("Total time:       %.3f seconds%n", totalSeconds);
    }
}
