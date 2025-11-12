# Instrumentation Flag Support

## Summary

ExecutionTracker and LocalDequeMonitor now support optional enable/disable flags, allowing a single implementation to work in both instrumented and non-instrumented modes.

## Changes Made

### ExecutionTracker
```java
// Before: Always enabled
ExecutionTracker tracker = new ExecutionTracker();

// After: Can be disabled
ExecutionTracker tracker = new ExecutionTracker(true);   // enabled (detailed stats)
ExecutionTracker tracker = new ExecutionTracker(false);  // disabled (no-op)
ExecutionTracker tracker = new ExecutionTracker();       // enabled (default)
```

### LocalDequeMonitor
```java
// Before: Always enabled
LocalDequeMonitor monitor = new LocalDequeMonitor();

// After: Can be disabled
LocalDequeMonitor monitor = new LocalDequeMonitor(true);   // enabled (track deques)
LocalDequeMonitor monitor = new LocalDequeMonitor(false);  // disabled (no-op)
LocalDequeMonitor monitor = new LocalDequeMonitor();       // enabled (default)
```

## Behavior

### When Enabled (true)
- `record()` collects timing data
- `sample()` tracks deque sizes
- `printReport()` prints full statistics
- `printExtremes()` prints slowest/fastest tasks
- `printSpatialHeatmap()` shows heatmap
- `printStatistics()` shows deque activity

### When Disabled (false)
- `record()` is a no-op (no collection)
- `sample()` is a no-op (no tracking)
- `printReport()` is a no-op (no output)
- `printExtremes()` is a no-op (no output)
- `printSpatialHeatmap()` is a no-op (no output)
- `printStatistics()` is a no-op (no output)
- **No performance overhead** - collections are null, checks are minimal

## Usage Pattern

### Single Implementation with Flag

```java
public class MandelbrotTileBased {
    private final ExecutionTracker tracker;
    private final boolean instrumented;

    public MandelbrotTileBased(int width, int height, int maxIterations, boolean instrumented) {
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
        this.instrumented = instrumented;
        this.tracker = new ExecutionTracker(instrumented);  // ← Flag
    }

    private class TileTask implements Callable<TileResult> {
        @Override
        public TileResult call() {
            long startTime = System.nanoTime();
            // ... do work ...
            long endTime = System.nanoTime();
            tracker.record(startTime, endTime, Thread.currentThread().getName());  // ← No-op if disabled
            return result;
        }
    }

    public BufferedImage generate(int numThreads, int tileSize) {
        // ... generate ...

        tracker.printReport();  // ← No-op if disabled

        // But ALWAYS print basic timing:
        System.out.printf("Total time: %.3f seconds%n", elapsedSeconds);
    }
}
```

### Command-Line Flag

```java
public static void main(String[] args) {
    boolean instrumented = false;  // default: no detailed stats

    if (args.length > 5 && args[5].equals("--instrumented")) {
        instrumented = true;
    }

    Mandelbrot mandelbrot = new Mandelbrot(width, height, maxIter, instrumented);
    mandelbrot.generate(...);
}
```

## Benefits

### 1. **Single Implementation**
- No need for separate `Mandelbrot` and `MandelbrotInstrumented` classes
- Reduces code duplication
- Easier maintenance

### 2. **Zero Overhead When Disabled**
- Collections are null (no memory allocation)
- All methods short-circuit immediately
- No performance impact

### 3. **Consistent API**
- Same code path for instrumented and non-instrumented
- Bugs affect both modes equally
- Easy to compare performance

### 4. **Flexible Usage**
- Enable instrumentation for debugging
- Disable for production/benchmarking
- Toggle per run without recompilation

## Migration Strategy

### Option 1: Keep Separate Files (Current)
```
MandelbrotTileBased.java              (non-instrumented)
MandelbrotTileBasedInstrumented.java  (instrumented)
```

**Pros**:
- Clear separation
- No breaking changes
- Students see both approaches

**Cons**:
- Code duplication
- Maintenance burden

### Option 2: Single File with Flag (Recommended)
```
MandelbrotTileBased.java  (with optional instrumentation flag)
```

**Pros**:
- No duplication
- Single source of truth
- Easy to maintain

**Cons**:
- Slightly more complex constructor
- Requires command-line flag handling

### Option 3: Hybrid (Best for Teaching)
```
MandelbrotTileBased.java              (non-instrumented, simple)
MandelbrotTileBasedInstrumented.java  (with flag, shows advanced usage)
```

**Pros**:
- Simple version for beginners
- Advanced version shows flags
- Demonstrates both approaches

## Recommendation

For this lab, I suggest **keeping the current structure** but documenting that:

1. **Reference implementations** use flags (show advanced usage)
2. **Student template** is simple (no flags needed)
3. **Documentation** explains both approaches

This gives students:
- A simple starting point (no instrumentation complexity)
- A complete example (instrumented versions with flags)
- Understanding of design tradeoffs

## Example Run Commands

```bash
# Non-instrumented (fast, minimal output)
./run.sh tilebased 800 600 1000 8 50

# Instrumented (detailed statistics)
./run.sh instrumented 800 600 1000 8 50
```

The flag approach would allow:
```bash
# Same program, different behavior
./run.sh tilebased 800 600 1000 8 50                    # fast
./run.sh tilebased 800 600 1000 8 50 --instrumented   # detailed
```

## Implementation Checklist

### ✅ Completed:
- ExecutionTracker supports enable/disable flag
- LocalDequeMonitor supports enable/disable flag
- Backward compatible (default = enabled)
- All methods handle disabled state
- Zero overhead when disabled

### ⏳ Optional (Next Steps):
- Update implementations to accept instrumentation flag
- Consolidate duplicate implementations
- Update run.sh to support --instrumented flag
- Update documentation

## Conclusion

The flag support is **ready to use** but consolidating implementations is **optional**. The current approach (separate files) works fine and may be clearer for teaching purposes.

The main benefit achieved: **flexibility** - implementations can now easily toggle instrumentation on/off without code changes.
