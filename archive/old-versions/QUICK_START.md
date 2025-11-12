# ForkJoin Pool Lab - Quick Start Guide

## Build and Run

```bash
# Make scripts executable and build
chmod +x build.sh run.sh
./build.sh

# Run instrumented ForkJoin version to see work-stealing in action
./run.sh fjinstrumented 800 600 1000 50
```

## What You'll See

The instrumented version shows:

1. **Task Execution Statistics**
   - Min/max/average task times
   - Variability ratio

2. **ForkJoinPool Statistics**
   - Parallelism level
   - **Steal count** (work redistributions)
   - Total tasks and distribution

3. **Per-Thread Work Distribution**
   - Tasks executed by each worker thread
   - Average and total time per thread
   - Shows how work-stealing balances the load

4. **Load Balance Efficiency**
   - Ideal vs actual thread times
   - Efficiency percentage
   - Automatic detection of poor balance

5. **Spatial Heatmap**
   - ASCII visualization of execution times
   - Shows which regions took longest

6. **Queue Dynamics Visualization**
   - Real-time monitoring of external submission queue
   - Shows how fast tasks are forked vs stolen
   - Timeline shows queue filling up then draining
   - Demonstrates work-stealing in action!

## Examples

### Good Load Balance (Small Tiles = 50×50)

```
ForkJoinPool stats:
  Parallelism:     15 threads
  Total tasks:     192
  Stolen tasks:    192 (100.0%)

Per-thread distribution: 7-17 tasks each
Per-thread total times: 54ms to 64ms (very close!)
Load balance efficiency: 91.0%

✓ Good load balance! Work-stealing helping distribute work.

Queue Dynamics:
  Peak: 184 tasks waiting to be stolen
  Timeline: ░▓████████████████▓▓▓▓▒▒▒▒▒▒▒▒▒░░░░░░
  (Queue starts full, gradually empties as threads steal work)
```

**Why it works**:
- Main thread forks all 192 tasks into its deque
- Worker threads steal from main thread's deque
- With 192 tasks for 15 threads, there's enough work to steal
- Each thread gets a fair share (7-17 tasks)
- Queue visualization shows sustained work-stealing activity
- Even if some tasks are slower, there's enough granularity to balance the load

### Poor Load Balance (Large Tiles = 200×200)

```
ForkJoinPool stats:
  Parallelism:     15 threads
  Total tasks:     12
  Stolen tasks:    12 (100.0%)

Per-thread distribution: only 1 task each (3 threads get nothing!)
Per-thread total times: 39ms to 141ms (huge difference!)
Load balance efficiency: ~30%

⚠ Poor load balancing detected!
Note: With 12 tasks on 15 threads,
      tiles may be too large for effective work-stealing.

Queue Dynamics:
  Peak: 10 tasks waiting
  Timeline: ░██▒▒
  (Queue empties almost immediately - not enough work!)
```

**Why it fails**:
- Only 12 tasks for 15 threads (some threads get nothing!)
- Each thread gets at most 1 task
- Queue drains instantly - no sustained work-stealing
- One slow tile (141ms at fractal boundary) dominates
- Other threads finish fast tasks (~40ms) and idle
- Work-stealing can't help - there's nothing left to steal!

## Key Insight

**Task granularity matters!** Too few tasks = work-stealing can't help. Too many tasks = overhead increases. The instrumented version helps you find the sweet spot.

## Lab Tasks

1. **Understand the problem** - Run different tile sizes
2. **Complete the template** - `src/student/MandelbrotForkJoinTiles.java`
3. **Compare performance** - Use `./run.sh compare`
4. **Analyze results** - See when work-stealing helps vs hurts

See [docs/LAB_INSTRUCTIONS.md](docs/LAB_INSTRUCTIONS.md) for full details.
