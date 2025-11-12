import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * SOLUTION: Tile-based parallel Mandelbrot generator using thread pools.
 * The image is divided into rectangular tiles for better load balancing.
 */
public class MandelbrotTileBasedSolution {
    private final int width;
    private final int height;
    private final int maxIterations;
    private final double xMin, xMax, yMin, yMax;

    // Track computation time for reporting
    private double lastComputationTimeSeconds = 0;

    public double getLastComputationTime() {
        return lastComputationTimeSeconds;
    }

    public MandelbrotTileBasedSolution(int width, int height, int maxIterations) {
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
        this.xMin = -2.5;
        this.xMax = 1.0;
        this.yMin = -1.0;
        this.yMax = 1.0;
    }


    /**
     * Represents a rectangular tile in the image.
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
     * Helper class to store tile computation results.
     */
    private static class TileResult {
        final Tile tile;
        final int[][] pixelData;

        public TileResult(Tile tile, int[][] pixelData) {
            this.tile = tile;
            this.pixelData = pixelData;
        }
    }

    /**
     * Task to compute a rectangular tile of the Mandelbrot fractal.
     */
    private class TileTask implements Callable<TileResult> {
        private final Tile tile;

        public TileTask(Tile tile) {
            this.tile = tile;
        }

        @Override
        public TileResult call() {
            int tileWidth = tile.getWidth();
            int tileHeight = tile.getHeight();
            int[][] pixelData = new int[tileHeight][tileWidth];

            for (int py = tile.startY; py < tile.endY; py++) {
                for (int px = tile.startX; px < tile.endX; px++) {
                    // Map pixel coordinates to complex plane
                    double cx = xMin + (xMax - xMin) * px / width;
                    double cy = yMin + (yMax - yMin) * py / height;

                    double iterations = MandelbrotUtils.computeIterations(cx, cy, maxIterations);
                    int color = MandelbrotUtils.iterationsToColor(iterations, maxIterations);

                    // Store in tile-local coordinates
                    pixelData[py - tile.startY][px - tile.startX] = color;
                }
            }

            return new TileResult(tile, pixelData);
        }
    }

    /**
     * Generate the Mandelbrot fractal using tile-based parallelization.
     */
    public BufferedImage generate(int numThreads, int tileSize) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Create a fixed thread pool
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        long startTime = System.nanoTime();

        try {
            // Divide the image into tiles
            List<Tile> tiles = new ArrayList<>();
            for (int y = 0; y < height; y += tileSize) {
                for (int x = 0; x < width; x += tileSize) {
                    int endX = Math.min(x + tileSize, width);
                    int endY = Math.min(y + tileSize, height);
                    tiles.add(new Tile(x, y, endX, endY));
                }
            }

            System.out.println("Total tiles: " + tiles.size());

            // Use ExecutorCompletionService to process results as they complete
            // This is more efficient than waiting for futures in submission order!
            ExecutorCompletionService<TileResult> completionService =
                new ExecutorCompletionService<>(executor);

            // Submit all tile tasks
            for (Tile tile : tiles) {
                completionService.submit(new TileTask(tile));
            }

            // Collect results AS THEY COMPLETE (not in submission order)
            // This processes fast tiles immediately without waiting for slow ones
            for (int i = 0; i < tiles.size(); i++) {
                TileResult result = completionService.take().get();
                Tile tile = result.tile;
                int[][] pixelData = result.pixelData;

                // Copy tile data to the image using bulk setRGB
                // Flatten 2D array into 1D for bulk operation
                int tileWidth = tile.getWidth();
                int tileHeight = tile.getHeight();
                int[] flatData = new int[tileWidth * tileHeight];

                for (int py = 0; py < tileHeight; py++) {
                    System.arraycopy(pixelData[py], 0, flatData, py * tileWidth, tileWidth);
                }

                // Use bulk setRGB - much faster than pixel-by-pixel!
                image.setRGB(tile.startX, tile.startY, tileWidth, tileHeight,
                            flatData, 0, tileWidth);
                //          ^startX     ^startY      ^w        ^h         ^data  ^offset ^scansize
            }

        } catch (InterruptedException | ExecutionException e) {
            System.err.println("Error during parallel computation: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shutdown the executor properly
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }

        long endTime = System.nanoTime();
        lastComputationTimeSeconds = (endTime - startTime) / 1_000_000_000.0;

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
        int numThreads = Runtime.getRuntime().availableProcessors();
        int tileSize = 30; 

        if (args.length >= 5) {
            width = Integer.parseInt(args[0]);
            height = Integer.parseInt(args[1]);
            maxIterations = Integer.parseInt(args[2]);
            numThreads = Integer.parseInt(args[3]);
            tileSize = Integer.parseInt(args[4]);
        }

        System.out.println("Tile-Based Parallel Mandelbrot Generation (SOLUTION)");
        System.out.println("Image size: " + width + "x" + height);
        System.out.println("Max iterations: " + maxIterations);
        System.out.println("Number of threads: " + numThreads);
        System.out.println("Tile size: " + tileSize + "x" + tileSize);
        System.out.println("----------------------------------------");

        MandelbrotTileBasedSolution mandelbrot = new MandelbrotTileBasedSolution(width, height, maxIterations);

        long totalStartTime = System.nanoTime();
        BufferedImage image = mandelbrot.generate(numThreads, tileSize);

        try {
            mandelbrot.saveImage(image, "mandelbrot_tilebased_solution_" + numThreads + "threads_tile" + tileSize + ".png");
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
