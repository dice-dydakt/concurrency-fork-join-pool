import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;

/**
 * ForkJoin implementation using BINARY SPLIT with optional instrumentation with ExecutionTracker.
 *
 * Use instrumented=false for production/benchmarking (minimal overhead).
 * Use instrumented=true for detailed statistics and analysis.
 */
public class MandelbrotForkJoinBinary {
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

    public MandelbrotForkJoinBinary(int width, int height, int maxIterations, boolean instrumented) {
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

    public MandelbrotForkJoinBinary(int width, int height, int maxIterations) {
        this(width, height, maxIterations, true);
    }

    private static class Region {
        final int startX, startY, endX, endY;

        public Region(int startX, int startY, int endX, int endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }

        public int getWidth() { return endX - startX; }
        public int getHeight() { return endY - startY; }
        public int getArea() { return getWidth() * getHeight(); }
    }

    /**
     * Instrumented RecursiveAction 
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
            dequeMonitor.sample();

            long startTime = System.nanoTime();  // ← Instrumentation line 1

            if (region.getArea() <= threshold) {
                computeRegion(region);
            } else {
                if (region.getWidth() >= region.getHeight()) {
                    int midX = (region.startX + region.endX) / 2;
                    MandelbrotTask leftTask = new MandelbrotTask(
                        new Region(region.startX, region.startY, midX, region.endY), threshold);
                    MandelbrotTask rightTask = new MandelbrotTask(
                        new Region(midX, region.startY, region.endX, region.endY), threshold);

                    dequeMonitor.sample();
                    leftTask.fork();
                    dequeMonitor.sample();
                    rightTask.compute();
                    leftTask.join();
                } else {
                    int midY = (region.startY + region.endY) / 2;
                    MandelbrotTask topTask = new MandelbrotTask(
                        new Region(region.startX, region.startY, region.endX, midY), threshold);
                    MandelbrotTask bottomTask = new MandelbrotTask(
                        new Region(region.startX, midY, region.endX, region.endY), threshold);

                    dequeMonitor.sample();
                    topTask.fork();
                    dequeMonitor.sample();
                    bottomTask.compute();
                    topTask.join();
                }
            }

            long endTime = System.nanoTime();  // ← Instrumentation line 2
            tracker.record(startTime, endTime, Thread.currentThread().getName());  // ← Instrumentation line 3

            dequeMonitor.sample();
        }

        private void computeRegion(Region r) {
            int regionWidth = r.endX - r.startX;
            int regionHeight = r.endY - r.startY;
            int[] pixels = new int[regionWidth * regionHeight];
            int index = 0;

            for (int py = r.startY; py < r.endY; py++) {
                for (int px = r.startX; px < r.endX; px++) {
                    double cx = xMin + (xMax - xMin) * px / (width - 1);
                    double cy = yMin + (yMax - yMin) * py / (height - 1);

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

        int parallelism = Runtime.getRuntime().availableProcessors();
        ForkJoinPool pool = new ForkJoinPool(parallelism);

        String mode = instrumented ? "INSTRUMENTED " : "";
        System.out.println(mode + "ForkJoin Mandelbrot (Binary Split - Recursive)");
        System.out.println("Image size: " + width + "x" + height);
        System.out.println("Max iterations: " + maxIterations);
        System.out.println("ForkJoinPool parallelism: " + pool.getParallelism());
        System.out.println("Threshold: " + threshold + " pixels");
        System.out.println("Split strategy: BINARY (recursive 2-way split)");
        if (instrumented) {
            System.out.println("Instrumentation: ENABLED (detailed statistics)");
        }
        System.out.println("----------------------------------------");

        Region fullImage = new Region(0, 0, width, height);
        MandelbrotTask rootTask = new MandelbrotTask(fullImage, threshold);

        long startTime = System.nanoTime();
        pool.invoke(rootTask);
        long endTime = System.nanoTime();

        lastComputationTimeSeconds = (endTime - startTime) / 1_000_000_000.0;

        pool.shutdown();

        // Print detailed statistics only if instrumented
        if (instrumented) {
            tracker.printReport();
            dequeMonitor.printStatistics();
        }

        return image;
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
            threshold = Integer.parseInt(args[argIndex++]);
        }

        MandelbrotForkJoinBinary mandelbrot =
            new MandelbrotForkJoinBinary(width, height, maxIterations, instrumented);

        long totalStartTime = System.nanoTime();
        BufferedImage image = mandelbrot.generate(threshold);

        try {
            mandelbrot.saveImage(image, "mandelbrot_forkjoin_binary_instrumented_threshold" + threshold + ".png");
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
