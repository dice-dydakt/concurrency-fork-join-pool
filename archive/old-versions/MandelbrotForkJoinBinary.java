import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;

/**
 * ForkJoin implementation using BINARY SPLIT instead of quadtree.
 * Splits regions in half along the longer dimension (width or height).
 *
 * Comparison with quadtree:
 * - Quadtree: Splits into 4 subtasks (2D subdivision)
 * - Binary: Splits into 2 subtasks (1D subdivision along longer axis)
 *
 * Benefits of binary split:
 * - Simpler subdivision logic
 * - Better for rectangular regions (adapts to aspect ratio)
 * - Fewer tasks created (depth × 2 vs depth × 4)
 * - Natural "fork 1, compute 1" pattern
 */
public class MandelbrotForkJoinBinary {
    private final int width;
    private final int height;
    private final int maxIterations;
    private final double xMin, xMax, yMin, yMax;
    private BufferedImage image;

    // Track computation time for reporting
    private double lastComputationTimeSeconds = 0;

    public double getLastComputationTime() {
        return lastComputationTimeSeconds;
    }

    public MandelbrotForkJoinBinary(int width, int height, int maxIterations) {
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
        this.xMin = -2.5;
        this.xMax = 1.0;
        this.yMin = -1.0;
        this.yMax = 1.0;
    }

    /**
     * Represents a rectangular region of the image.
     */
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

        @Override
        public String toString() {
            return String.format("Region[(%d,%d)-(%d,%d) %dx%d=%d pixels]",
                startX, startY, endX, endY, getWidth(), getHeight(), getArea());
        }
    }

    /**
     * RecursiveAction that computes Mandelbrot fractal using BINARY SPLIT.
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
            // Base case: region small enough, compute directly
            if (region.getArea() <= threshold) {
                computeRegion(region);
                return;
            }

            // Recursive case: BINARY SPLIT along longer dimension
            // This adapts to region aspect ratio for balanced subdivision
            if (region.getWidth() >= region.getHeight()) {
                // Region is wider - split vertically (into left/right)
                int midX = (region.startX + region.endX) / 2;

                MandelbrotTask leftTask = new MandelbrotTask(
                    new Region(region.startX, region.startY, midX, region.endY), threshold);
                MandelbrotTask rightTask = new MandelbrotTask(
                    new Region(midX, region.startY, region.endX, region.endY), threshold);

                // OPTIMIZATION: Fork 1, compute 1 on current thread
                leftTask.fork();
                rightTask.compute();  // Do right half on current thread
                leftTask.join();      // Wait for left half

            } else {
                // Region is taller - split horizontally (into top/bottom)
                int midY = (region.startY + region.endY) / 2;

                MandelbrotTask topTask = new MandelbrotTask(
                    new Region(region.startX, region.startY, region.endX, midY), threshold);
                MandelbrotTask bottomTask = new MandelbrotTask(
                    new Region(region.startX, midY, region.endX, region.endY), threshold);

                // OPTIMIZATION: Fork 1, compute 1 on current thread
                topTask.fork();
                bottomTask.compute();  // Do bottom half on current thread
                topTask.join();        // Wait for top half
            }
        }

        /**
         * Compute all pixels in the given region using hybrid approach:
         * 1. Compute to local array (cache-friendly)
         * 2. Bulk write to shared image (efficient)
         */
        private void computeRegion(Region r) {
            int regionWidth = r.endX - r.startX;
            int regionHeight = r.endY - r.startY;
            int[] pixels = new int[regionWidth * regionHeight];
            int index = 0;

            for (int py = r.startY; py < r.endY; py++) {
                for (int px = r.startX; px < r.endX; px++) {
                    // Map pixel coordinates to complex plane
                    double cx = xMin + (xMax - xMin) * px / width;
                    double cy = yMin + (yMax - yMin) * py / height;

                    double iterations = MandelbrotUtils.computeIterations(cx, cy, maxIterations);
                    int color = MandelbrotUtils.iterationsToColor(iterations, maxIterations);

                    pixels[index++] = color;
                }
            }

            // Write entire region at once using bulk setRGB - much faster!
            // This is safe because each task writes to non-overlapping regions
            image.setRGB(r.startX, r.startY, regionWidth, regionHeight,
                        pixels, 0, regionWidth);
        }
    }

    /**
     * Generate the Mandelbrot fractal using ForkJoinPool with BINARY SPLIT.
     *
     * @param threshold The area threshold below which a region is computed directly
     *                  (instead of being subdivided further)
     */
    public BufferedImage generate(int threshold) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ForkJoinPool pool = ForkJoinPool.commonPool();  // Common pool doesn't need shutdown

        System.out.println("ForkJoinPool parallelism: " + pool.getParallelism());
        System.out.println("Threshold: " + threshold + " pixels");
        System.out.println("Split strategy: BINARY (2-way split along longer dimension)");

        Region fullImage = new Region(0, 0, width, height);
        MandelbrotTask rootTask = new MandelbrotTask(fullImage, threshold);

        long startTime = System.nanoTime();
        pool.invoke(rootTask);
        long endTime = System.nanoTime();

        lastComputationTimeSeconds = (endTime - startTime) / 1_000_000_000.0;

        return image;
    }

    /**
     * Alternative: Generate using custom ForkJoinPool with specified parallelism.
     */
    public BufferedImage generateWithCustomPool(int parallelism, int threshold) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        System.out.println("Custom ForkJoinPool parallelism: " + pool.getParallelism());
        System.out.println("Threshold: " + threshold + " pixels");
        System.out.println("Split strategy: BINARY (2-way split along longer dimension)");

        try {
            Region fullImage = new Region(0, 0, width, height);
            MandelbrotTask rootTask = new MandelbrotTask(fullImage, threshold);

            long startTime = System.nanoTime();
            pool.invoke(rootTask);
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

        // area threshold for which to stop splitting task (compute directly)
        // sweet spot should be between 0.5x and 2x 
        // where x = (width * hegiht) / (cores * 16)
        int threshold = 5000;

        if (args.length >= 3) {
            width = Integer.parseInt(args[0]);
            height = Integer.parseInt(args[1]);
            maxIterations = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            threshold = Integer.parseInt(args[3]);
        }

        System.out.println("ForkJoinPool Mandelbrot (BINARY SPLIT)");
        System.out.println("Image size: " + width + "x" + height);
        System.out.println("Max iterations: " + maxIterations);
        System.out.println("----------------------------------------");

        MandelbrotForkJoinBinary mandelbrot =
            new MandelbrotForkJoinBinary(width, height, maxIterations);

        long totalStartTime = System.nanoTime();
        BufferedImage image = mandelbrot.generate(threshold);

        try {
            mandelbrot.saveImage(image, "mandelbrot_forkjoin_binary_threshold" + threshold + ".png");
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
