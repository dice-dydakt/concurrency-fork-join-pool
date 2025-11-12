import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;

/**
 * STUDENT TEMPLATE: ForkJoin implementation using PRE-COMPUTED TILES.
 *
 * Learning objectives:
 * 1. Understand ForkJoin work-stealing with static task decomposition
 * 2. Compare work-stealing benefit vs overhead
 * 3. Explore impact of task granularity (tile size)
 *
 * Key difference from recursive ForkJoin:
 * - NO recursive task splitting
 * - All tiles computed upfront (like ThreadPool version)
 * - Work-stealing handles load balancing dynamically
 *
 * TODO: Complete the implementation following the instructions marked with TODO
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
    }

    /**
     * RecursiveAction that computes a single tile.
     *
     * TODO 1: Complete the TileTask class
     * - Extend RecursiveAction (ForkJoin task with no return value)
     * - Implement compute() method to call computeTile()
     * - No recursive subdivision needed!
     */
    private class TileTask extends RecursiveAction {
        private final Tile tile;

        public TileTask(Tile tile) {
            this.tile = tile;
        }

        @Override
        protected void compute() {
            // TODO 1a: Call computeTile(tile) to process this tile
            // Hint: This is a simple one-liner - no recursion!

        }

        /**
         * Compute all pixels in the tile using hybrid approach:
         * 1. Compute to local array (cache-friendly)
         * 2. Bulk write to shared image (efficient)
         *
         * TODO 2: Complete the computeTile method
         */
        private void computeTile(Tile t) {
            int tileWidth = t.endX - t.startX;
            int tileHeight = t.endY - t.startY;

            // TODO 2a: Create local pixel array
            // Hint: Size should be tileWidth * tileHeight
            int[] pixels = null;  // REPLACE THIS

            int index = 0;

            // TODO 2b: Compute all pixels in the tile
            // Hint: Nested loop over py (t.startY to t.endY) and px (t.startX to t.endX)
            // For each pixel:
            //   1. Map to complex plane (cx, cy)
            //   2. Compute iterations using MandelbrotUtils.computeIterations()
            //   3. Convert to color using MandelbrotUtils.iterationsToColor()
            //   4. Store in pixels[index++]

            // YOUR CODE HERE


            // TODO 2c: Bulk write to image
            // Hint: Use image.setRGB(startX, startY, width, height, pixels, offset, scansize)
            // Remember: offset=0, scansize=tileWidth

            // YOUR CODE HERE

        }
    }

    /**
     * Generate the Mandelbrot fractal using ForkJoinPool with pre-computed tiles.
     *
     * TODO 3: Complete the generate method
     *
     * @param tileSize Size of each tile in pixels
     */
    public BufferedImage generate(int tileSize) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ForkJoinPool pool = ForkJoinPool.commonPool();

        System.out.println("ForkJoinPool parallelism: " + pool.getParallelism());
        System.out.println("Tile size: " + tileSize + "x" + tileSize);
        System.out.println("Strategy: PRE-COMPUTED TILES (no recursive subdivision)");

        // TODO 3a: Create all tiles upfront
        // Hint: Nested loop over y and x coordinates (increment by tileSize)
        // Use Math.min(x + tileSize, width) to handle edge tiles
        List<TileTask> tasks = new ArrayList<>();
        int tilesX = 0;
        int tilesY = 0;

        // YOUR CODE HERE
        // for (int y = ...) {
        //     for (int x = ...) {
        //         ...
        //     }
        // }


        int totalTiles = tasks.size();
        System.out.println("Total tiles: " + totalTiles + " (" + tilesX + "x" + tilesY + " grid)");
        System.out.println("Avg pixels per tile: " + ((width * height) / totalTiles));

        long startTime = System.nanoTime();

        // TODO 3b: Fork and join all tasks
        // Hint: Use ForkJoinTask.invokeAll(tasks) - it handles fork+join for you
        // Alternative: Loop with task.fork(), then loop with task.join()
        // YOUR CODE HERE


        long endTime = System.nanoTime();

        double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
        System.out.printf("Computation time: %.3f seconds%n", elapsedSeconds);

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
