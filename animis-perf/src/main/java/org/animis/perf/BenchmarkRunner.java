package org.animis.perf;

import org.openjdk.jmh.Main;

public final class BenchmarkRunner {
  private BenchmarkRunner() {
  }

  public static void main(final String[] args) throws Exception {
    if (args != null && args.length > 0) {
      Main.main(args);
      return;
    }
    Main.main(new String[] {
        "AnimatorUpdateBenchmark.update100Joints",
        "MotionMatcherBenchmark.findBest10kFrames",
        "ClipSamplerBenchmark.sample60SecondClip",
        "SkinningComputerBenchmark.compute100Joints",
        "-wi", "2",
        "-i", "4",
        "-f", "0",
        "-r", "1s",
        "-w", "1s",
        "-rf", "csv",
        "-rff", "perf/latest.csv"
    });
  }
}
