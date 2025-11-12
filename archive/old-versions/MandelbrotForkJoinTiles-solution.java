import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ForkJoin implementation using PRE-COMPUTED TILES instead of recursive subdivision.
 *
 * Key difference from other ForkJoin implementations:
 * - NO recursive task splitting
 * - Tiles computed upfront (like ThreadPool version)
 * - All tasks created at once and submitted to ForkJoinPool
 * - Work-stealing still active, but no dynamic subdivision overhead
 *
 * This isolates the benefit of work-stealing from recursive task creation overhead.
 *
 * Comparison:
 * - ForkJoin Quadtree/Binary: Recursive subdivision, dynamic task creation
 * - ForkJoin Tiles (this): Static tiles, all tasks created upfront
 * - ThreadPool Tiles: Static tiles, fixed thread assignment
 */
public class MandelbrotForkJoinTiles {
    private final int width;
    private final int height;
    private final int maxIterations;
    private final double xMin, xMax, yMin, yMax;
    private BufferedImage image;

    public MandelbrotForkJoinTiles(int width, int height, int maxIterations) {
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
        this.xMin = -2.5;
        this.xMax = 1.0;
        this.yMin = -1.0;
        this.yMax = 1.0;
    }

    /**
     * Represents a tile (rectangular region) to compute.
     */
    private static class Tile {
        final int startX, startY;
        final int endX, endY;

        public Tile(int startX, int startY, int endX, int endY) {
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

    /**
     * RecursiveAction that computes a single tile.
     * No subdivision - just computes the assigned tile.
     */
    private class TileTask extends RecursiveAction {
        private final Tile tile;

        public TileTask(Tile tile) {
            this.tile = tile;
        }

        @Override
        protected void compute() {
            // Just compute the tile - no recursive splitting!
            computeTile(tile);
        }

        /**
         * Compute all pixels in the tile using hybrid approach:
         * 1. Compute to local array (cache-friendly)
         * 2. Bulk write to shared image (efficient)
         */
        private void computeTile(Tile t) {
            int tileWidth = t.endX - t.startX;
            int tileHeight = t.endY - t.startY;
            int[] pixels = new int[tileWidth * tileHeight];
            int index = 0;

            for (int py = t.startY; py < t.endY; py++) {
                for (int px = t.startX; px < t.endX; px++) {
                    // Map pixel coordinates to complex plane
                    double cx = xMin + (xMax - xMin) * px / width;
                    double cy = yMin + (yMax - yMin) * py / height;

                    double iterations = MandelbrotUtils.computeIterations(cx, cy, maxIterations);
                    int color = MandelbrotUtils.iterationsToColor(iterations, maxIterations);

                    pixels[index++] = color;
                }
            }

            // Write entire tile at once using bulk setRGB
            image.setRGB(t.startX, t.startY, tileWidth, tileHeight,
                        pixels, 0, tileWidth);
        }
    }

    /**
     * Generate the Mandelbrot fractal using ForkJoinPool with pre-computed tiles.
     *
     * @param tileSize Size of each tile in pixels
     */
    public BufferedImage generate(int tileSize) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ForkJoinPool pool = ForkJoinPool.commonPool();

        System.out.println("ForkJoinPool parallelism: " + pool.getParallelism());
        System.out.println("Tile size: " + tileSize + "x" + tileSize);
        System.out.println("Strategy: PRE-COMPUTED TILES (no recursive subdivision)");

        // Create all tiles upfront (like ThreadPool version)
        List<TileTask> tasks = new ArrayList<>();
        int tilesX = 0;
        int tilesY = 0;

        for (int y = 0; y < height; y += tileSize) {
            if (y == 0) tilesX = 0;
            for (int x = 0; x < width; x += tileSize) {
                int endX = Math.min(x + tileSize, width);
                int endY = Math.min(y + tileSize, height);

                Tile tile = new Tile(x, y, endX, endY);
                tasks.add(new TileTask(tile));

                if (y == 0) tilesX++;
            }
            tilesY++;
        }

        int totalTiles = tasks.size();
        System.out.println("Total tiles: " + totalTiles + " (" + tilesX + "x" + tilesY + " grid)");
        System.out.println("Avg pixels per tile: " + ((width * height) / totalTiles));

        long startTime = System.nanoTime();

        // Fork and join all tasks using invokeAll (more efficient than separate fork+join loops)
        ForkJoinTask.invokeAll(tasks);

        long endTime = System.nanoTime();

        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("Computation time: %.3f seconds%n", elapsedSeconds);

        return image;
    }

    /**
     * Alternative: Generate using custom ForkJoinPool with specified parallelism.
     */
    public BufferedImage generateWithCustomPool(int parallelism, int tileSize) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        System.out.println("Custom ForkJoinPool parallelism: " + pool.getParallelism());
        System.out.println("Tile size: " + tileSize + "x" + tileSize);
        System.out.println("Strategy: PRE-COMPUTED TILES (no recursive subdivision)");

        // Create all tiles upfront
        List<TileTask> tasks = new ArrayList<>();
        int tilesX = 0;
        int tilesY = 0;

        for (int y = 0; y < height; y += tileSize) {
            if (y == 0) tilesX = 0;
            for (int x = 0; x < width; x += tileSize) {
                int endX = Math.min(x + tileSize, width);
                int endY = Math.min(y + tileSize, height);

                Tile tile = new Tile(x, y, endX, endY);
                tasks.add(new TileTask(tile));

                if (y == 0) tilesX++;
            }
            tilesY++;
        }

        int totalTiles = tasks.size();
        System.out.println("Total tiles: " + totalTiles + " (" + tilesX + "x" + tilesY + " grid)");

        try {
            long startTime = System.nanoTime();

            // Submit all tasks to custom pool
            pool.invoke(new RecursiveAction() {
                @Override
                protected void compute() {
                    ForkJoinTask.invokeAll(tasks);
                }
            });

            long endTime = System.nanoTime();

            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
            System.out.printf("Computation time: %.3f seconds%n", elapsedSeconds);

        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
            }
        }

        return image;
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
        int tileSize = 50;

        if (args.length >= 3) {
            width = Integer.parseInt(args[0]);
            height = Integer.parseInt(args[1]);
            maxIterations = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            tileSize = Integer.parseInt(args[3]);
        }

        System.out.println("ForkJoinPool Mandelbrot (PRE-COMPUTED TILES)");
        System.out.println("Image size: " + width + "x" + height);
        System.out.println("Max iterations: " + maxIterations);
        System.out.println("----------------------------------------");

        MandelbrotForkJoinTiles mandelbrot =
            new MandelbrotForkJoinTiles(width, height, maxIterations);

        long startTime = System.nanoTime();
        BufferedImage image = mandelbrot.generate(tileSize);
        long endTime = System.nanoTime();

        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("Total time: %.3f seconds%n", elapsedSeconds);

        try {
            mandelbrot.saveImage(image, "mandelbrot_forkjoin_tiles_" + tileSize + ".png");
        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
        }
    }
}
