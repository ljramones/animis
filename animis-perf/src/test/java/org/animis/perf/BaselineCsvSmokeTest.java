package org.animis.perf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BaselineCsvSmokeTest {
  @Test
  void baselineHasRequiredBenchmarks() throws IOException {
    final Path baseline = Path.of("..", "perf", "baseline.csv").toAbsolutePath().normalize();
    assertTrue(Files.exists(baseline), "Missing baseline file: " + baseline);

    final List<String> lines = Files.readAllLines(baseline);
    assertTrue(lines.stream().anyMatch(l -> l.contains("AnimatorUpdateBenchmark.update100Joints")));
    assertTrue(lines.stream().anyMatch(l -> l.contains("MotionMatcherBenchmark.findBest10kFrames")));
    assertTrue(lines.stream().anyMatch(l -> l.contains("ClipSamplerBenchmark.sample60SecondClip")));
    assertTrue(lines.stream().anyMatch(l -> l.contains("SkinningComputerBenchmark.compute100Joints")));
  }
}
