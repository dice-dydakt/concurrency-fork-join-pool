#!/bin/bash
# Run script for ForkJoin Pool Lab

if [ $# -lt 1 ]; then
    echo "Usage: ./run.sh <program> [args...]"
    echo ""
    echo "Available programs:"
    echo "  tilebased       - Run ThreadPool reference (tile-based)"
    echo "  instrumented    - Run ThreadPool with visualization (heatmap, stats)"
    echo "  binary          - Run ForkJoin reference (binary split)"
    echo "  binaryinstr     - Run ForkJoin binary with instrumentation (shows deque growth!)"
    echo "  fjtiles         - Run ForkJoin reference (pre-computed tiles)"
    echo "  fjtilesinstr    - Run ForkJoin tiles with instrumentation"
    echo "  benchmark       - Run comprehensive performance benchmark (generates CSV)"
    echo ""
    echo "Examples:"
    echo "  ./run.sh tilebased 1600 1200 2000 16 100"
    echo "  ./run.sh instrumented 1600 1200 2000 16 100"
    echo "  ./run.sh binary 1600 1200 2000 500"
    echo "  ./run.sh binaryinstr 1600 1200 2000 500"
    echo "  ./run.sh fjtiles 1600 1200 2000 50"
    echo "  ./run.sh fjtilesinstr 1600 1200 2000 50"
    echo "  ./run.sh benchmark"
    echo ""
    echo "Arguments:"
    echo "  tilebased: <width> <height> <maxIter> <numThreads> <tileSize>"
    echo "  binary:    <width> <height> <maxIter> <threshold>"
    echo "  fjtiles:   <width> <height> <maxIter> <tileSize>"
    echo "  benchmark: (no arguments - runs predefined test suite)"
    exit 1
fi

# Build if needed
if [ ! -d "bin" ]; then
    echo "Building project..."
    ./build.sh
    echo ""
fi

PROGRAM=$1
shift

case $PROGRAM in
    tilebased)
        java -cp bin MandelbrotTileBased "$@"
        ;;
    instrumented)
        java -cp bin MandelbrotTileBased "$@" --instrumented
        ;;
    binary)
        java -cp bin MandelbrotForkJoinBinary "$@"
        ;;
    binaryinstr)
        java --add-opens java.base/java.util.concurrent=ALL-UNNAMED -cp bin MandelbrotForkJoinBinary "$@" --instrumented
        ;;
    fjtiles)
        java -cp bin-reference:bin MandelbrotForkJoinTiles "$@"
        ;;
    fjtilesinstr)
        java --add-opens java.base/java.util.concurrent=ALL-UNNAMED -cp bin-reference:bin MandelbrotForkJoinTiles "$@" --instrumented
        ;;
    benchmark)
        java -cp bin-reference:bin Benchmark "$@"
        ;;
    *)
        echo "Unknown program: $PROGRAM"
        echo "Run './run.sh' without arguments to see usage."
        exit 1
        ;;
esac
