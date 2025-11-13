#!/bin/bash
# Build script for ForkJoin Pool Lab

set -e  # Exit on error

echo "Building ForkJoin Pool Lab..."
echo "=============================="

# Create bin directories if they don't exist
mkdir -p bin
mkdir -p bin-reference

# Compile utilities
echo "Compiling utilities..."
javac -d bin src/utils/MandelbrotUtils.java
javac -d bin src/utils/LocalDequeMonitor.java
javac -d bin src/utils/ExecutionTracker.java

# Compile reference implementations
echo "Compiling reference implementations..."
javac -cp bin -d bin src/reference/MandelbrotTileBased.java
javac -cp bin -d bin src/reference/MandelbrotForkJoinBinary.java
javac -cp bin -d bin-reference src/reference/MandelbrotForkJoinTiles.java

# Compile benchmark
# echo "Compiling benchmark..."
# javac -cp bin:bin-reference -d bin src/Benchmark.java

echo ""
echo "Build complete!"
echo "Compiled classes are in: bin/ and bin-reference/"
