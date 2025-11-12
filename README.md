# ForkJoin Pool Lab - Mandelbrot Fractal Generation

Explore Java ForkJoin framework and work-stealing algorithms through Mandelbrot set generation. Compare ThreadPool vs ForkJoin implementations with different task decomposition strategies.

## Project Structure

```
concurrency-fork-join-pool/
├── src/
│   ├── utils/
│   │   ├── MandelbrotUtils.java      # Mandelbrot computation utilities
│   │   ├── ExecutionTracker.java     # Performance tracking & statistics
│   │   └── LocalDequeMonitor.java    # ForkJoin deque monitoring
│   └── reference/
│       ├── MandelbrotTileBased.java      # ThreadPool with fixed tiles
│       ├── MandelbrotForkJoinBinary.java # ForkJoin with recursive split
│       └── MandelbrotForkJoinTiles.java  # ForkJoin with pre-computed tiles
├── build.sh    # Build script
└── run.sh      # Run script
```

## Quick Start

### Build

```bash
chmod +x build.sh run.sh
./build.sh
```

### Run

All implementations support optional `--instrumented` flag for detailed statistics.

**ThreadPool (tile-based):**
```bash
./run.sh tilebased 1600 1200 2000 16 100
# Arguments: width height maxIter numThreads tileSize

./run.sh instrumented 1600 1200 2000 16 100
# With statistics and heatmap
```

**ForkJoin (binary split):**
```bash
./run.sh binary 1600 1200 2000 5000
# Arguments: width height maxIter threshold

./run.sh binaryinstr 1600 1200 2000 5000
# With statistics and deque monitoring
```

**ForkJoin (pre-computed tiles):**
```bash
./run.sh fjtiles 1600 1200 2000 50
# Arguments: width height maxIter tileSize

./run.sh fjtilesinstr 1600 1200 2000 50
# With statistics and deque monitoring
```

## Key Features

- **Three implementations**: ThreadPool, ForkJoin binary split, ForkJoin tiles
- **Optional instrumentation**: Enable/disable detailed statistics with `--instrumented` flag
- **Comprehensive statistics**: Execution time distribution, load balance, spatial heatmap
- **Work-stealing analysis**: Local deque monitoring for ForkJoin implementations

## Implementation Comparison

| Implementation | Task Decomposition | Work Distribution | Best For |
|---------------|-------------------|-------------------|----------|
| **TileBased** | Fixed tiles upfront | Static (no stealing) | Uniform workloads |
| **ForkJoinBinary** | Recursive split | Dynamic (work-stealing) | Irregular workloads |
| **ForkJoinTiles** | Fixed tiles upfront | Dynamic (work-stealing) | Hybrid approach |

## Output

All implementations generate PNG images of the Mandelbrot set and report timing:

```
Computation time: 0.823 seconds
Total time:       1.012 seconds
```

With `--instrumented` flag, also shows:
- Task execution statistics (min, max, avg, median, percentiles)
- Per-thread load distribution
- Spatial heatmap of execution times
- Work-stealing activity (ForkJoin only)

## Requirements

- Java 11 or later
- Bash (Linux/macOS/WSL)
