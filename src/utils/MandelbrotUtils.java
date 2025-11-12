/**
 * Shared utility methods for Mandelbrot fractal computation and coloring.
 * This class contains the core algorithms used by all implementations.
 */
public class MandelbrotUtils {

    /**
     * Compute the number of iterations for a given complex number c.
     * Returns a double for smooth/continuous coloring.
     *
     * @param cx Real part of complex number c
     * @param cy Imaginary part of complex number c
     * @param maxIterations Maximum number of iterations before considering point to be in the set
     * @return Smooth iteration count (can be fractional for smooth coloring)
     */
    public static double computeIterations(double cx, double cy, int maxIterations) {
        double zx = 0, zy = 0;
        int iterations = 0;

        while (zx * zx + zy * zy < 4.0 && iterations < maxIterations) {
            double temp = zx * zx - zy * zy + cx;
            zy = 2.0 * zx * zy + cy;
            zx = temp;
            iterations++;
        }

        // Smooth coloring using normalized iteration count
        if (iterations < maxIterations) {
            double log_zn = Math.log(zx * zx + zy * zy) / 2.0;
            double nu = Math.log(log_zn / Math.log(2)) / Math.log(2);
            return iterations + 1 - nu;
        }

        return iterations;
    }

    /**
     * Map iteration count to the classic Mandelbrot color scheme.
     * Creates deep blue background with electric blue-cyan-white edges (like the famous images!)
     *
     * @param iterations The iteration count (can be fractional for smooth coloring)
     * @param maxIterations Maximum iterations (used for normalization)
     * @return RGB color as an integer (0xRRGGBB format)
     */
    public static int iterationsToColor(double iterations, int maxIterations) {
        if (iterations >= maxIterations) {
            return 0x000000; // Black for points in the set
        }

        // Use logarithmic scale for better color distribution
        double t = Math.log(iterations + 1) / Math.log(maxIterations + 1);

        // Blue background with golden/red lightning at the edges
        // Low iterations = deep blue, high iterations = red→yellow→white "lightning"
        double hue, saturation, value;

        if (t < 0.5) {
            // Deep blue background for low iteration counts
            hue = 220;  // Deep blue
            saturation = 1.0 - t * 0.4;  // High saturation
            value = 0.2 + t * 0.6;  // Dark to medium brightness
        } else {
            // Golden/red lightning for high iteration counts (the edges)
            double t2 = (t - 0.5) * 2.0;  // Remap 0.5-1.0 to 0-1
            hue = 60 - t2 * 60;  // Yellow (60°) to red (0°)
            saturation = 1.0 - t2 * 0.8;  // Fade to white
            value = 0.6 + t2 * 0.4 + Math.pow(t2, 2) * 0.3;  // Bright with glow boost
        }

        return hsvToRgb(hue, saturation, value);
    }

    /**
     * Convert HSV color to RGB.
     *
     * @param h Hue (0-360 degrees)
     * @param s Saturation (0-1)
     * @param v Value/Brightness (0-1)
     * @return RGB color as an integer (0xRRGGBB format)
     */
    public static int hsvToRgb(double h, double s, double v) {
        double c = v * s;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = v - c;

        double r = 0, g = 0, b = 0;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }

        int ri = (int) ((r + m) * 255);
        int gi = (int) ((g + m) * 255);
        int bi = (int) ((b + m) * 255);

        return (ri << 16) | (gi << 8) | bi;
    }
}
